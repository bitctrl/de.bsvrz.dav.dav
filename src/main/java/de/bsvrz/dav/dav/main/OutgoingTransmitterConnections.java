/*
 * Copyright 2015 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.dav.dav.
 * 
 * de.bsvrz.dav.dav is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.dav.dav is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.dav.dav.  If not, see <http://www.gnu.org/licenses/>.

 * Contact Information:
 * Kappich Systemberatung
 * Martin-Luther-Straße 14
 * 52062 Aachen, Germany
 * phone: +49 241 4090 436 
 * mail: <info@kappich.de>
 */

package de.bsvrz.dav.dav.main;

import de.bsvrz.dav.daf.communication.lowLevel.ConnectionInterface;
import de.bsvrz.dav.daf.communication.lowLevel.LowLevelCommunication;
import de.bsvrz.dav.daf.communication.lowLevel.ServerConnectionInterface;
import de.bsvrz.dav.daf.main.CommunicationError;
import de.bsvrz.dav.daf.main.ConnectionException;
import de.bsvrz.dav.daf.main.impl.config.telegrams.TransmitterConnectionInfo;
import de.bsvrz.dav.daf.main.impl.config.telegrams.TransmitterInfo;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunication;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Verwaltung für ausgehende Datenverteilerverbindungen
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class OutgoingTransmitterConnections extends AbstractTransmitterConnections {

	private static final Debug _debug = Debug.getLogger();

	/**
	 * Threadpool, der nicht etablierte Verbindungen aufbaut
	 */
	private final ScheduledExecutorService _transmitterReconnectService = Executors.newScheduledThreadPool(1);

	/**
	 * Verwaltung aller offenen Verbindungen
	 */
	private final TransmitterConnectionMap _connections;

	/**
	 * Factory zum Aufbauen von Verbindungen (z.B. {@link de.bsvrz.dav.dav.communication.tcpCommunication.TCP_IP_ServerCommunication})
	 */
	private final ServerConnectionInterface _serverConnection;
	
	/**
	 * Wartezeit in ms, bevor versucht wird, eine abgebrochene Verbindung wiederherzustellen
	 */
	private final int _reconnectionDelay;

	/**
	 * Konstruktor
	 * @param serverConnection Factory zum Aufbauen von Verbindungen (z.B. {@link de.bsvrz.dav.dav.communication.tcpCommunication.TCP_IP_ServerCommunication})
	 * @param reconnectionDelay Wiederverbindungswartezeit nach Verbindungsabbruch
	 * @param serverDavParameters  Server-Parameter
	 * @param lowLevelConnectionsManager Low-Level-Verbindungsverwaltung
	 * @param transmitterManager High-Level-Verwaltung für Dav-Dav-Verbindungen (Anmeldelisten usw.)
	 * @param connectionMap Verwaltung für alle offenen Verbindungen pro Datenverteiler
	 */
	public OutgoingTransmitterConnections(final ServerConnectionInterface serverConnection, final int reconnectionDelay, final ServerDavParameters serverDavParameters, final LowLevelConnectionsManagerInterface lowLevelConnectionsManager, final HighLevelTransmitterManager transmitterManager, final TransmitterConnectionMap connectionMap) {
		super(serverDavParameters, lowLevelConnectionsManager, transmitterManager);
		_serverConnection = serverConnection;
		_reconnectionDelay = reconnectionDelay;
		_connections = connectionMap;
	}

	public void start() {
		for(final TransmitterConnectionInfo info : _connections.getAllInfos()) {
			if(info.isExchangeConnection()) {
				continue;
			}
			if(!info.isActiveConnection()) {
				continue;
			}
			TransmitterInfo t1 = info.getTransmitter_1();
			if(t1.getTransmitterId() == _connections.getMyTransmitterId()) {
				if(!connectToMainTransmitter(info)) {
					connectToAlternativeTransmitters(info);
					scheduleTransmitterConnect(info, _reconnectionDelay, TimeUnit.MILLISECONDS);
				}
			}
		}
	}

	/**
	 * Wird aufgerufen, wenn die ID des Remote-Transmitters bekannt wird (d. h. die Authentifizierung abgeschlossen ist)
	 *
	 * @param communication
	 */
	public void updateId(final T_T_HighLevelCommunication communication) {
		for(TransmitterConnectionInfo info : _connections.getAllInfos()) {
			if(_connections.getConnection(info) == communication && communication.getId() != info.getTransmitter_2().getTransmitterId()) {
				_connections.removeConnection(info.getTransmitter_2().getTransmitterId(), communication);
				_debug.warning("Verbundener Datenverteiler unter " + communication.getRemoteAdress() + ":" + communication.getRemoteSubadress() + " hat die falsche ID: " + communication.getId());
			}
		}
		_connections.putConnection(communication.getId(), communication);

	}


	/**
	 * Startet den Verbindungsaufbau zwischen zwei direkt benachbarten Datenverteilern. Beim Verbindungsaufbau zwischen zwei DAV werden durch die Angabe der beiden Kommunikationspartner, die Wichtung der Verbindung, die Angabe, welche(r) Datenverteiler die Verbindung aufbaut und
	 * die Spezifikation von Ersatzverbindungen festgelegt, um welche Art von Verrbindung es sich handelt.
	 *
	 * @param transmitterConnectionInfo Enthält Informationen zu der Verbindungart zwischen zwei Datenverteilern.
	 * @return true: Verbindung hergestellt, false: Verbindung nicht hergestellt
	 * @see #connectToTransmitter(TransmitterInfo, short, long, String)
	 */
	private boolean connectToMainTransmitter(final TransmitterConnectionInfo transmitterConnectionInfo) {
		final TransmitterInfo t2 = transmitterConnectionInfo.getTransmitter_2();
		final short weight = transmitterConnectionInfo.getWeight();
		final long waitingTime = transmitterConnectionInfo.getConnectionTimeThreshold();
		return connectToTransmitter(t2, weight, waitingTime, transmitterConnectionInfo.getUserName());
	}

	/**
	 * Startet den Ersatzverbindungsaufbau zwischen zwei nicht direkt benachbarten Datenverteilern. Beim Verbindungsaufbau zwischen zwei DAV werden durch die Angabe der beiden Kommunikationspartner, die Wichtung der Verbindung, die Angabe, welche(r) Datenverteiler die Verbindung
	 * aufbaut und die Spezifikation von Ersatzverbindungen festgelegt, um welche Art von Verrbindung es sich handelt. Ob Ersatzverbindungen automatisch etabliert werden sollen, wird durch das autoExchangeTransmitterDetection Flag festgelegt.
	 *
	 * @param transmitterConnectionInfo Enthält Informationen zu der Verbindungart zwischen zwei Datenverteilern.
	 * @see #connectToTransmitter(TransmitterInfo, short, long, String)
	 */
	private void connectToAlternativeTransmitters(final TransmitterConnectionInfo transmitterConnectionInfo) {
		final TransmitterInfo t2 = transmitterConnectionInfo.getTransmitter_2();
		if(transmitterConnectionInfo.isAutoExchangeTransmitterDetectionOn()) {
			final List<TransmitterConnectionInfo> infos = getInvolvedTransmitters(t2);
			for(final TransmitterConnectionInfo info : infos) {
				final TransmitterInfo transmitterInfo = info.getTransmitter_2();
				final short weight = info.getWeight();
				final long time = info.getConnectionTimeThreshold();
				if(transmitterInfo != null) {
					connectToTransmitter(transmitterInfo, weight, time, transmitterConnectionInfo.getUserName());
				}
			}
		}
		else {
			final TransmitterInfo[] infos = transmitterConnectionInfo.getExchangeTransmitterList();
			if(infos != null) {
				for(final TransmitterInfo info : infos) {
					TransmitterConnectionInfo tmpTransmitterConnectionInfo = null;
					for(final TransmitterConnectionInfo _transmitterConnectionInfo : _connections.getAllInfos()) {
						if(_transmitterConnectionInfo.isExchangeConnection()
								&& (_transmitterConnectionInfo.getTransmitter_1().getTransmitterId() == _connections.getMyTransmitterId()) && (
								_transmitterConnectionInfo.getTransmitter_2().getTransmitterId() == info.getTransmitterId())) {
							tmpTransmitterConnectionInfo = _transmitterConnectionInfo;
							break;
						}
					}
					if(tmpTransmitterConnectionInfo != null) {
						final short weight = tmpTransmitterConnectionInfo.getWeight();
						final long time = tmpTransmitterConnectionInfo.getConnectionTimeThreshold();
						_connections.getActiveExchangeConnections().add(tmpTransmitterConnectionInfo);
						connectToTransmitter(info, weight, time, tmpTransmitterConnectionInfo.getUserName());
					}
				}
			}
		}
	}


	/**
	 * Erstellt ein Array, das die Informationen über die benachbarten Datenverteiler des übergebenen Datenverteilers enthält.
	 *
	 * @param transmitterInfo Information zum Datenverteiler
	 * @return Liste mit benachbarten Datenverteilern
	 */
	private List<TransmitterConnectionInfo> getInvolvedTransmitters(final TransmitterInfo transmitterInfo) {
		final List<TransmitterConnectionInfo> list = new ArrayList<TransmitterConnectionInfo>();
		for(final TransmitterConnectionInfo _transmitterConnectionInfo1 : _connections.getAllInfos()) {
			if((_transmitterConnectionInfo1 == null) || _transmitterConnectionInfo1.isExchangeConnection()) {
				continue;
			}
			final TransmitterInfo t1 = _transmitterConnectionInfo1.getTransmitter_1();
			if(t1.getTransmitterId() == transmitterInfo.getTransmitterId()) {
				final TransmitterInfo t2 = _transmitterConnectionInfo1.getTransmitter_2();
				if(t2 != null) {
					for(final TransmitterConnectionInfo _transmitterConnectionInfo : _connections.getAllInfos()) {
						if(_transmitterConnectionInfo == null) {
							continue;
						}
						if(_transmitterConnectionInfo.isExchangeConnection()) {
							final TransmitterInfo _t1 = _transmitterConnectionInfo.getTransmitter_1();
							final TransmitterInfo _t2 = _transmitterConnectionInfo.getTransmitter_2();
							if((_t1.getTransmitterId() == _connections.getMyTransmitterId()) && (_t2.getTransmitterId() == t2.getTransmitterId())) {
								list.add(_transmitterConnectionInfo);
								break;
							}
						}
					}
				}
			}
		}
		return list;
	}


	private T_T_HighLevelCommunication startTransmitterConnection(
			final TransmitterInfo transmitterInfo, final short weight, final String userName, final String password, final int subAddressToConnectTo)
			throws ConnectionException {
		final ConnectionInterface connection = _serverConnection.getPlainConnection();
		final LowLevelCommunication lowLevelCommunication = createLowLevelCommunication(connection, false);
		final ServerConnectionProperties properties = new ServerConnectionProperties(
				lowLevelCommunication, _lowLevelConnectionsManager.getLowLevelAuthentication().getAuthenticationComponent(), _serverDavParameters
		);
		lowLevelCommunication.connect(transmitterInfo.getAdress(), subAddressToConnectTo);
		return createTransmitterHighLevelCommunication(weight, userName, password, properties, false);
	}

	/**
	 * Startet den Verbindungsaufbau zwischen zwei Datenverteilern. Falls keine Verbindung etabliert werden konnte, wird eine entsprechende Exception gefangen
	 *
	 * @param transmitterInfo Information zum Datenverteiler
	 * @param weight          Die Information wird von der Wegverwaltung benutzt, wenn eine Verbindung bewertet wird.
	 * @param time            Zeitspanne in der versucht werden soll eine Verbindung aufzubauen, in Millisekunden. Minimale Wartezeit eine Sekunde.
	 * @param userName        Benutzername mit dem die Authentifizierung durchgeführt werden soll.
	 * @return true, wenn Verbindung hergestellt werden konnte; false, wenn Verbindung nicht hergestellt werden konnte.
	 * @see #connectToTransmitter(TransmitterInfo, short, String, String)
	 */
	private boolean connectToTransmitter(final TransmitterInfo transmitterInfo, final short weight, final long time, String userName) {
		final String password;
		if("".equals(userName)) {
			userName = _serverDavParameters.getUserName();
			password = _serverDavParameters.getUserPassword();
		}
		else {
			password = _serverDavParameters.getStoredPassword(userName);
			if(password == null) {
				_debug.error(
						"Passwort des Benutzers " + userName + " konnte nicht ermittelt werden. Es wird gebraucht für Datenverteilerkopplung mit " + transmitterInfo
				);
				return false;
			}
		}
		_debug.info("Starte Datenverteilerkopplung als Benutzer " + userName + " zu ", transmitterInfo);
		_debug.finer(" time", time);
		_debug.finer(" weight", weight);
		long waitingTime = time;
		if(waitingTime < 1000) {
			waitingTime = 1000;
		}
		final long startTime = System.currentTimeMillis();
		do {
			try {
				connectToTransmitter(transmitterInfo, weight, userName, password);
				return true;
			}
			catch(ConnectionException | CommunicationError ex) {
				_debug.warning("Verbindung zum " + transmitterInfo + " konnte nicht aufgebaut werden", ex);
				_lowLevelConnectionsManager.updateCommunicationState();
				if(System.getProperty("agent.name") != null) {
					// Wenn aus Testumgebung gestartet
					System.out.println("Verbindung zum " + transmitterInfo + " konnte nicht aufgebaut werden: " + ex);
					ex.printStackTrace();
				}
				try {
					Thread.sleep(_reconnectionDelay);
				}
				catch(InterruptedException e) {
					return false;
				}
				waitingTime -= (System.currentTimeMillis() - startTime);
			}
		}
		while(waitingTime > 0);
		return false;
	}

	/**
	 * Etabliert Verbindung zwischen zwei Datenverteilern. Falls keine Verbindung aufgebaut werden konnte, wird eine entsprechende Ausnahme geworfen.
	 *
	 * @param transmitterInfo Informationen zum Datenverteiler.
	 * @param weight          Die Information wird von der Wegverwaltung benutzt, wenn eine Verbindung bewertet wird.
	 * @param userName        Benutzername mit dem die Authentifizierung durchgeführt werden soll.
	 * @param password        Passwort des Benutzers mit dem die Authentifizierung durchgeführt werden soll.
	 * @throws de.bsvrz.dav.daf.main.ConnectionException wenn eine Verbindung nicht aufgebaut werden konnte
	 * @throws de.bsvrz.dav.daf.main.CommunicationError  wenn bei der initialen Kommunikation mit dem Datenverteiler Fehler aufgetreten sind
	 */
	private void connectToTransmitter(final TransmitterInfo transmitterInfo, final short weight, final String userName, final String password)
			throws ConnectionException, CommunicationError {
		if(_serverConnection == null) {
			throw new IllegalArgumentException("Die Verwaltung ist nicht richtig initialisiert.");
		}

		final long tId = transmitterInfo.getTransmitterId();

		int subAddressToConnectTo = transmitterInfo.getSubAdress();
		if(subAddressToConnectTo == 100000) {
			// Zu Testzwecken wird die Portnummer mit dem Wert 100000 serverseitig durch 8088 und clientseitig durch 8081 ersetzt
			subAddressToConnectTo = 8081;
		}
		subAddressToConnectTo += _serverDavParameters.getTransmitterConnectionsSubAddressOffset();

		for(final T_T_HighLevelCommunication transmitterConnection : _connections.getAllConnections()) {
			if(transmitterConnection != null && !transmitterConnection.isClosed()) {
				if(transmitterConnection.getId() == tId) {
					// Verbindugn mit dem angegebenen Datenverteiler besteht bereits
					return;
				}
				final String adress = transmitterConnection.getRemoteAdress();
				final int subAdress = transmitterConnection.getRemoteSubadress();
				if((adress != null) && (adress.equals(transmitterInfo.getAdress())) && (subAddressToConnectTo == subAdress)) {
					// Verbindung mit einem Datenverteiler mit der angegebenen Adresse besteht bereits
					return;
				}
			}
		}

		if(_connections.isDisabled(transmitterInfo.getTransmitterId())) {
			return;
		}

		final T_T_HighLevelCommunication highLevelCommunication = startTransmitterConnection(transmitterInfo, weight, userName, password, subAddressToConnectTo);
		_connections.putConnection(tId, highLevelCommunication);
		_lowLevelConnectionsManager.updateCommunicationState();
		highLevelCommunication.connect();
		highLevelCommunication.completeInitialisation();

		_debug.info("Verbindungsaufbau zum " + transmitterInfo + " war erfolgreich");
	}

	private void disableReplacementConnection(final TransmitterConnectionInfo transmitterConnectionInfo) {
		if(transmitterConnectionInfo.isAutoExchangeTransmitterDetectionOn()) {
			final TransmitterInfo t2 = transmitterConnectionInfo.getTransmitter_2();
			final List<TransmitterConnectionInfo> infos = getInvolvedTransmitters(t2);
			for(final TransmitterConnectionInfo info : infos) {
				try {
					final TransmitterInfo transmitterInfo = info.getTransmitter_2();
					if(transmitterInfo != null) {
						terminateReplacementConnection(transmitterInfo, true);
					}
				}
				catch(Exception e) {
					// Sollte nicht auftreten, da connection.terminate() keine Exception wirft
					_debug.error("Ersatzverbindung konnte nicht terminiert werden", e);
				}
			}
		}
		else {
			final TransmitterInfo[] infos = transmitterConnectionInfo.getExchangeTransmitterList();
			if(infos != null) {
				for(final TransmitterInfo info : infos) {
					TransmitterConnectionInfo transmitterConnectionInfoToDisconnect = null;
					for(final TransmitterConnectionInfo _transmitterConnectionInfo : _connections.getAllInfos()) {
						if(_transmitterConnectionInfo.isExchangeConnection()
								&& (_transmitterConnectionInfo.getTransmitter_1().getTransmitterId() == _connections.getMyTransmitterId()) && (
								_transmitterConnectionInfo.getTransmitter_2().getTransmitterId() == info.getTransmitterId())) {
							transmitterConnectionInfoToDisconnect = _transmitterConnectionInfo;
							break;
						}
					}
					if(transmitterConnectionInfoToDisconnect != null) {
						try {
							_connections.getActiveExchangeConnections().remove(transmitterConnectionInfoToDisconnect);
							terminateReplacementConnection(info, false);
						}
						catch(Exception e) {
							// Sollte nicht auftreten, da connection.terminate() keine Exception wirft
							_debug.error("Ersatzverbindung konnte nicht terminiert werden", e);
						}
					}
				}
			}
		}
	}

	private void terminateReplacementConnection(final TransmitterInfo transmitterInfo, final boolean automatic) {
		final T_T_HighLevelCommunication connection = _connections.getConnection(transmitterInfo.getTransmitterId());
		if((connection != null) && (!connection.isIncomingConnection())) {
			connection.terminate(
					false,
					(automatic ? "Automatisch ermittelte " : "Konfigurierte ")
							+ "Ersatzverbindung wird nicht mehr benötigt, weil ursprüngliche Verbindung wiederhergestellt wurde"
			);
		}
	}

	public void close() {
		_transmitterReconnectService.shutdown();
	}

	class TransmitterReconnectionTask implements Runnable {

		private final TransmitterConnectionInfo _transmitterConnectionInfo;

		public TransmitterReconnectionTask(TransmitterConnectionInfo transmitterConnectionInfo) {
			_transmitterConnectionInfo = transmitterConnectionInfo;
		}

		/**
		 * Behandelt den Verbindungsaufbau mit einem entfernten Datenverteiler (Transmitter)
		 */
		@Override
		public final void run() {
			if(_lowLevelConnectionsManager.isClosing()) return;

			if(_transmitterConnectionInfo != null) {
				if(connectToMainTransmitter(_transmitterConnectionInfo)) {
					// Verbindung erfolgreich wiederhergestellt, Ersatzverbindungen (falls vorhanden) entfernen.
					disableReplacementConnection(_transmitterConnectionInfo);
				}
				else {
					try {
						// Verbindung kann nicht aufgebaut werden, sicherstellen, dass eventuelle Ersatzverbindungen aufgebaut werden.
						connectToAlternativeTransmitters(_transmitterConnectionInfo);
					}
					finally {
						// Nach ein paar Sekunden neuen Verbindungsversuch starten.
						scheduleTransmitterConnect(_transmitterConnectionInfo, _reconnectionDelay, TimeUnit.MILLISECONDS);
					}
				}
			}
		}
	}

	void scheduleTransmitterConnect(final TransmitterConnectionInfo transmitterConnectionInfo, final int delay, final TimeUnit timeUnit) {
		if(_lowLevelConnectionsManager.isClosing()) return;
		_transmitterReconnectService.schedule(new TransmitterReconnectionTask(transmitterConnectionInfo), delay, timeUnit);
	}


}
