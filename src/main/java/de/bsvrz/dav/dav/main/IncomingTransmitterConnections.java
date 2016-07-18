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
import de.bsvrz.dav.daf.main.ConnectionException;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunication;
import de.bsvrz.sys.funclib.debug.Debug;

/**
 * Klasse, die eingehende Dav-Dav-Verbindungen verwaltet
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class IncomingTransmitterConnections extends AbstractTransmitterConnections {

	private static final Debug _debug = Debug.getLogger();

	/**
	 * "Gewicht" für Verbindungen für die kein konfiguriertes Gewicht bekannt ist (für Routen, Anmeldelisten und ähnliches)
	 */
	private static final short DEFAULT_WEIGHT = 1;

	/**
	 *  Thread, der eingehedne Verbindungen verarbeitet
	 */
	private final TransmitterConnectionsSubscriber _transmitterConnectionsSubscriber;
	
	/**
	 * Die Serverkommunikationskomponente, die eingehende Verbindungen akzeptiert.
	 */
	private final ServerConnectionInterface _transmittersServerConnection;

	/**
	 * Verwaltung für alle offenen Verbindungen pro Datenverteiler
	 */
	private final TransmitterConnectionMap _connections;

	/**
	 * Konstruktor
	 * @param transmittersServerConnection Die Serverkommunikationskomponente, die eingehende Verbindungen akzeptiert. 
	 * @param serverDavParameters  Server-Parameter
	 * @param lowLevelConnectionsManager Low-Level-Verbindungsverwaltung
	 * @param transmitterManager High-Level-Verwaltung für Dav-Dav-Verbindungen (Anmeldelisten usw.)
	 * @param connectionMap Verwaltung für alle offenen Verbindungen pro Datenverteiler
	 */
	public IncomingTransmitterConnections(final ServerConnectionInterface transmittersServerConnection, final ServerDavParameters serverDavParameters, final LowLevelConnectionsManagerInterface lowLevelConnectionsManager, final HighLevelTransmitterManager transmitterManager, final TransmitterConnectionMap connectionMap) {
		super(serverDavParameters, lowLevelConnectionsManager, transmitterManager);
		this._transmittersServerConnection = transmittersServerConnection;

		_transmitterConnectionsSubscriber = new TransmitterConnectionsSubscriber();
		_connections = connectionMap;
	}

	public void start() {
		_transmitterConnectionsSubscriber.start();
	}

	/**
	 * Wird bei eingehender Verbindung ausgeführt
	 *
	 * @param connection Verbindung
	 * @throws ConnectionException
	 */
	private T_T_HighLevelCommunication startTransmitterConnection(final ConnectionInterface connection) throws ConnectionException {
		final LowLevelCommunication lowLevelCommunication = createLowLevelCommunication(connection, true);
		final ServerConnectionProperties properties = new ServerConnectionProperties(
				lowLevelCommunication, _lowLevelConnectionsManager.getLowLevelAuthentication().getAuthenticationComponent(), _serverDavParameters
		);
		return createTransmitterHighLevelCommunication(DEFAULT_WEIGHT, "", "", properties, true);
	}

	/**
	 * Wird aufgerufen, wenn die ID des Remote-Transmitters bekannt wird (d. h. die Authentifizierung abgeschlossen ist)
	 * @param communication
	 */
	public void updateId(final T_T_HighLevelCommunication communication) {
		T_T_HighLevelCommunication existingCommunication = _connections.getConnection(communication.getId());
		if(existingCommunication != null && existingCommunication != communication && !existingCommunication.isClosed()){
			communication.terminate(true, "Es gibt bereits eine Verbindung von diesem Datenverteiler");
			return;
		}
		_connections.putConnection(communication.getId(), communication);
	}

	public void close() {
		_transmitterConnectionsSubscriber.interrupt();
		_transmittersServerConnection.disconnect();
	}

	class TransmitterConnectionsSubscriber extends Thread {

		public TransmitterConnectionsSubscriber() {
			super("TransmitterConnectionsSubscriber");
		}

		/**
		 * The run method that loops through
		 */
		@Override
		public final void run() {
			if(_transmittersServerConnection == null) {
				return;
			}
			while(!isInterrupted()) {
				// Blocks until a connection occurs:
				final ConnectionInterface connection = _transmittersServerConnection.accept();
				final Runnable runnable = new Runnable() {
					@Override
					public void run() {
						if(connection != null) {
							try {
								T_T_HighLevelCommunication communication = startTransmitterConnection(connection);
								_debug.fine("Neue eingehende Verbindung von " + communication.getRemoteAdress() + ":" + communication.getRemoteSubadress());
								_lowLevelConnectionsManager.updateCommunicationState();
							}
							catch(ConnectionException ex) {
								_debug.warning("Fehler beim Aufbau einer eingehenden Datenverteilerverbindung von " + connection.getMainAdress() + ":" + connection.getSubAdressNumber(), ex);
							}
						}
					}
				};
				final Thread thread = new Thread(runnable);
				thread.start();
			}
		}
	}
}
