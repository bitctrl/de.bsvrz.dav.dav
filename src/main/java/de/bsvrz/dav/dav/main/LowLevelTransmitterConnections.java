/*
 * Copyright 2011 by Kappich Systemberatung Aachen
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

import de.bsvrz.dav.daf.communication.lowLevel.ParameterizedConnectionInterface;
import de.bsvrz.dav.daf.communication.lowLevel.ServerConnectionInterface;
import de.bsvrz.dav.daf.main.CommunicationError;
import de.bsvrz.dav.daf.main.impl.config.telegrams.TransmitterConnectionInfo;
import de.bsvrz.dav.daf.main.impl.config.telegrams.TransmitterInfo;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunication;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.*;
import java.util.concurrent.*;

/**
 * Diese Klasse ist die Low-level-Verwaltung für Datenverteiler-Datenverteiler-Verbindungen
 * Sie kümmert sich um den Verbindungsaufbau und um dem Aufbau bei Ersatzverbindungen im Falle eines Fehlers. 
 * 
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public final class LowLevelTransmitterConnections {

	private static final Debug _debug = Debug.getLogger();

	private static final short DEFAULT_WEIGHT = 1;
	
	/** Wiederverbindungswartezeit in Millisekunden*/
	private final int _reconnectionDelay;
	
	/** Verwaltung für eingehende Verbindungen */
	private IncomingTransmitterConnections _incomingTransmitterConnections = null;
	
	/** Verwaltung für ausgehende Verbindungen */
	private OutgoingTransmitterConnections _outgoingTransmitterConnections = null;

	/** High-Level-Verwaltung für Dav-Dav Verbindungen */
	private final HighLevelTransmitterManager _transmitterManager;

	/** Parameter des Datenverteilers */
	private final ServerDavParameters _serverDavParameters;

	/** Referenz auf den LowLevelConnectionsManager (Allgemeine Verbindungsverwaltung) */
	private final LowLevelConnectionsManagerInterface _lowLevelConnectionsManager;

	/** Eigene Datenverteiler-ID */
	private final long _myTransmitterId;
	
	/** Hilfsklasse für die Zuordnung zwischen (aktiven) Verbindungen und konfigurierten Verbindungen */
	private TransmitterConnectionMap _transmitterConnectionMap = null;

	/**
	 * Konstruktor
	 * @param transmitterManager High-Level-Verwaltung
	 * @param serverDavParameters Parameter
	 * @param lowLevelConnectionsManager Low-Level-Verwaltung
	 */
	public LowLevelTransmitterConnections(
			final HighLevelTransmitterManager transmitterManager,
			final ServerDavParameters serverDavParameters,
			final LowLevelConnectionsManagerInterface lowLevelConnectionsManager) {
		_transmitterManager = transmitterManager;
		_serverDavParameters = serverDavParameters;
		_lowLevelConnectionsManager = lowLevelConnectionsManager;
		_myTransmitterId = transmitterManager.getMyTransmitterId();
	    long reconnectInterDavDelay = _serverDavParameters.getReconnectInterDavDelay();
	    if(reconnectInterDavDelay < 0 || reconnectInterDavDelay > Integer.MAX_VALUE){
		    throw new IllegalArgumentException("Ungültige Wiederverbindungs-Wartezeit: " + reconnectInterDavDelay + "ms");
	    }
	    _reconnectionDelay = (int) reconnectInterDavDelay;
	}

	/**
	 * Startet den Aufbau der Dav-Dav-Verbindungen
	 * @param communicationProtocolClass Kommunikationsprotokoll-Klasse
	 * @param transmitterConnectionInfos Konfigurierte Dav-Dav-Verbindungen
	 * @param disabledConnections Deaktivierte Verbindungen
	 * @throws InstantiationException Wenn das Kommunikationsprotokoll nicht erzeugt werden kann (benötigt öffentlichen, parameterlosen Konstruktor)
	 * @throws IllegalAccessException Wenn das Kommunikationsprotokoll nicht erzeugt werden kann (benötigt öffentlichen, parameterlosen Konstruktor)
	 * @throws CommunicationError Wenn beim Aufbau der Serververbindung ein Fehler auftritt (z.B. Port bereits belegt)
	 */
	public void startTransmitterConnections(final Class<? extends ServerConnectionInterface> communicationProtocolClass, final TransmitterConnectionInfo[] transmitterConnectionInfos, final Collection<Long> disabledConnections) throws InstantiationException, IllegalAccessException, CommunicationError {

		_transmitterConnectionMap = new TransmitterConnectionMap(transmitterConnectionInfos, _myTransmitterId);

		_transmitterConnectionMap.getDisabledConnections().addAll(disabledConnections);

		final ServerConnectionInterface serverConnection = communicationProtocolClass.newInstance();

		final String communicationParameters2 = _serverDavParameters.getLowLevelCommunicationParameters();
		if(communicationParameters2.length() != 0 && serverConnection instanceof ParameterizedConnectionInterface) {
			final ParameterizedConnectionInterface connectionInterface = (ParameterizedConnectionInterface)serverConnection;
			connectionInterface.setParameters(communicationParameters2);
		}

		serverConnection.connect(getListenSubadress(transmitterConnectionInfos));

		_incomingTransmitterConnections = new IncomingTransmitterConnections(serverConnection, _serverDavParameters, _lowLevelConnectionsManager, _transmitterManager, _transmitterConnectionMap);

		_outgoingTransmitterConnections = new OutgoingTransmitterConnections(serverConnection, _reconnectionDelay, _serverDavParameters, _lowLevelConnectionsManager, _transmitterManager, _transmitterConnectionMap);


		_incomingTransmitterConnections.start();
		_outgoingTransmitterConnections.start();
	}

	/**
	 * Bestimmt den Port, auf dem der Server auf eingehende Verbindungen lauscht.
	 * @param transmitterConnectionInfos Verbindungsinfos
	 * @return Port
	 */
	private int getListenSubadress(final TransmitterConnectionInfo[] transmitterConnectionInfos) {
		int davDavSubadress = analyseConnectionInfosAndGetSubadress(transmitterConnectionInfos);

		if(davDavSubadress == -1) {
			davDavSubadress = this._serverDavParameters.getTransmitterConnectionsSubAddress();
		}
		int subAddressToListenFor = davDavSubadress;
		if(subAddressToListenFor == 100000) {
			// Zu Testzwecken wird die Portnummer mit dem Wert 100000 serverseitig durch 8088 und clientseitig durch 8081 ersetzt
			subAddressToListenFor = 8088;
		}

		subAddressToListenFor += _serverDavParameters.getTransmitterConnectionsSubAddressOffset();
		return subAddressToListenFor;
	}

	private int analyseConnectionInfosAndGetSubadress(final TransmitterConnectionInfo[] _transmitterConnectionInfos) {
		int davDavSubadress = -1;
		for(int i = 0; i < _transmitterConnectionInfos.length; ++i) {
			_debug.finer("Datenverteilerverbindung", _transmitterConnectionInfos[i].parseToString());
			if(_transmitterConnectionInfos[i] != null) {
				final TransmitterInfo transmitterInfo_1 = _transmitterConnectionInfos[i].getTransmitter_1();
				final TransmitterInfo transmitterInfo_2 = _transmitterConnectionInfos[i].getTransmitter_2();
				final long id1 = transmitterInfo_1.getTransmitterId();
				final long id2 = transmitterInfo_2.getTransmitterId();
				if(id1 == id2) {
					close(true, "Inkonsistente Netztopologie (Verbindung von Datenverteiler[" + id1 + "] zu sich selbst");
				}
				final int subAdresse1 = transmitterInfo_1.getSubAdress();
				final int subAdresse2 = transmitterInfo_2.getSubAdress();
				for(int j = i + 1; j < _transmitterConnectionInfos.length; ++j) {
					if(_transmitterConnectionInfos[j] != null) {
						final long tmpId1 = _transmitterConnectionInfos[j].getTransmitter_1().getTransmitterId();
						final long tmpId2 = _transmitterConnectionInfos[j].getTransmitter_2().getTransmitterId();
						if((id1 == tmpId1) && (id2 == tmpId2)) {
							close(
									true,
									"Inkonsistente Netztopologie (Mehrfache Verbindung zwichen Datenverteiler[" + id1 + "] und Datenverteiler[" + id2
									+ "] möglich)"
							);
						}
					}
				}
				if(id1 == _myTransmitterId) {
					if(davDavSubadress == -1) {
						davDavSubadress = subAdresse1;
					}
					else if(davDavSubadress != subAdresse1) {
						close(
								true, "Inkonsistente Netztopologie (Es wurden dem Datenverteiler[" + id1 + "] verschiedene Subadressen zugewiesen"
						);
					}
				}
				if(id2 == _myTransmitterId) {
					if(davDavSubadress == -1) {
						davDavSubadress = subAdresse2;
					}
					else if(davDavSubadress != subAdresse2) {
						close(
								true, "Inkonsistente Netztopologie (Es wurden dem Datenverteiler[" + id2 + "] verschiedene Subadressen zugewiesen"
						);
					}
				}
			}
		}
		return davDavSubadress;
	}

	/**
	 * Diese Methode wird von der Protokollsteuerung aufgerufen, um einer Verbindung ein Gewicht zuzuweisen. Die Information wird von der Wegverwaltung benutzt,
	 * wenn eine Verbindung bewertet wird.
	 *
	 * @param connectedTransmitterId ID des DAV
	 *
	 * @return Gewichtung der Verbindung
	 */
	public synchronized final short getWeight(final long connectedTransmitterId) {
		short weight = DEFAULT_WEIGHT;
		if(_transmitterConnectionMap != null) {
			TransmitterConnectionInfo info = _transmitterConnectionMap.getInfo(connectedTransmitterId);
			if(info != null) {
				return info.getWeight();
			}
		}
		return weight;
	}


	public synchronized void close(final boolean error, final String message) {
		if(_incomingTransmitterConnections != null) {
			_incomingTransmitterConnections.close();
		}
		if(_outgoingTransmitterConnections != null) {
			_outgoingTransmitterConnections.close();
		}
		if(_transmitterConnectionMap != null) {
			for(final T_T_HighLevelCommunication transmitterConnection : _transmitterConnectionMap.getAllConnections()) {
				transmitterConnection.terminate(error, message);
			}
			_transmitterConnectionMap.close();
		}
	}



	/**
	 * Entfernt die angegebene Verbindung, weil diese terminiert wurde
	 * @param transmitterCommunication Verbindung
	 */
	public synchronized void removeTransmitterConnection(final T_T_HighLevelCommunication transmitterCommunication) {

//		_transmitterConnectionMap.removeConnection(transmitterCommunication.getId(), transmitterCommunication);
		
		TransmitterConnectionInfo info = _transmitterConnectionMap.getInfo(transmitterCommunication);
		
		if(!transmitterCommunication.isIncomingConnection()
				&& info != null 
				&& !info.isExchangeConnection()) {
			// Ausgehende Verbindung wiederherstellen
			_outgoingTransmitterConnections.scheduleTransmitterConnect(info, _reconnectionDelay, TimeUnit.MILLISECONDS);
		}

	}

	public synchronized Collection<T_T_HighLevelCommunication> getTransmitterConnections() {
		return _transmitterConnectionMap.getAllConnections();
	}

	public synchronized void updateId(final T_T_HighLevelCommunication communication) {
		if(communication.isIncomingConnection()) {
			_incomingTransmitterConnections.updateId(communication);
		}
		else {
			_outgoingTransmitterConnections.updateId(communication);
		}
	}

	public synchronized Set<Long> getDisabledTransmitterConnections() {
		if(_transmitterConnectionMap == null) return Collections.emptySet();
		return new HashSet<Long>(_transmitterConnectionMap.getDisabledConnections());
	}
	
	public void setDisabledTransmitterConnections(Collection<Long> disabledConnections) {
		if(_transmitterConnectionMap == null) {
			// Noch nicht initialisiert. Bei der Initialisierung in startTransmitterConnections
			// werden die deaktivierten Verbindungen automatisch nochmal gesetzt, also muss man sich die hier
			// nicht merken...			
			return;
		}
		
		if(disabledConnections.contains(null)) throw new NullPointerException();
		Set<Long> oldSet = getDisabledTransmitterConnections();
		for(Long disabledConnection : disabledConnections) {
			if(!oldSet.contains(disabledConnection)){
				disableConnection(disabledConnection);
			}
		}
		for(Long oldConnection : oldSet) {
			if(!disabledConnections.contains(oldConnection)){
				enableConnection(oldConnection);
			}
		}
	}

	public synchronized void enableConnection(final long davId) {
		if(!_transmitterConnectionMap.getDisabledConnections().remove(davId)) return;
		for(TransmitterConnectionInfo transmitterConnectionInfo : _transmitterConnectionMap.getAllInfos()) {
			if(transmitterConnectionInfo.getTransmitter_2().getTransmitterId() == davId) {
				_outgoingTransmitterConnections.scheduleTransmitterConnect(transmitterConnectionInfo, 0, TimeUnit.SECONDS);
			}
		}
	}

	public synchronized void disableConnection(final long davId) {
		if(!_transmitterConnectionMap.getDisabledConnections().add(davId)) return;
		T_T_HighLevelCommunication connection = _transmitterConnectionMap.getConnection(davId);
		if(connection == null) return;      
		connection.terminate(true, "Verbindung wurde deaktiviert");
	}


	public synchronized Map<TransmitterInfo, CommunicationStateAndMessage> getStateMap() {
		final Map<TransmitterInfo, CommunicationStateAndMessage> result = new LinkedHashMap<TransmitterInfo, CommunicationStateAndMessage>();
		for(TransmitterConnectionInfo info : _transmitterConnectionMap.getAllInfos()) {
			putTransmitterInMap(result, info.getTransmitter_1());
			putTransmitterInMap(result, info.getTransmitter_2());
		}
		return result;
	}

	private void putTransmitterInMap(final Map<TransmitterInfo, CommunicationStateAndMessage> result, final TransmitterInfo transmitterInfo) {
		if(transmitterInfo.getTransmitterId() != _transmitterConnectionMap.getMyTransmitterId()){
			if(!result.containsKey(transmitterInfo)) {
				result.put(transmitterInfo, _transmitterConnectionMap.getState(transmitterInfo.getTransmitterId()));
			}
		}
	}

	public TransmitterConnectionInfo getTransmitterConnectionInfo(final long connectedTransmitterId) {
		return _transmitterConnectionMap.getTransmitterConnectionInfo(connectedTransmitterId);
	}

	public TransmitterConnectionInfo getRemoteTransmitterConnectionInfo(final long connectedTransmitterId) {
		return _transmitterConnectionMap.getRemoteTransmitterConnectionInfo(connectedTransmitterId);
	}

	public T_T_HighLevelCommunication getTransmitterConnection(final long transmitterId) {
		return _transmitterConnectionMap.getConnection(transmitterId);
	}
}
