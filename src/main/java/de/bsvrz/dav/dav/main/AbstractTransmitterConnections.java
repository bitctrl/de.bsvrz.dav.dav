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
import de.bsvrz.dav.daf.main.ConnectionException;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunication;

/**
 * Abstrakte Basisklasse für der Verwaltung von Dav-Dav-Verbindungen. Enthält ein paar Hilfsfunktionen für den Aufbau von Verbindungen. 
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
class AbstractTransmitterConnections {

	final ServerDavParameters _serverDavParameters;
	final LowLevelConnectionsManagerInterface _lowLevelConnectionsManager;
	final HighLevelTransmitterManagerInterface _transmitterManager;

	/** 
	 * Erstellt eine neue Instanz
	 * 
	 * @param serverDavParameters Parameter des datenverteilers auf Serverseite
	 * @param lowLevelConnectionsManager Low-Level-Verbindungsverwaltung
	 * @param transmitterManager High-Level-Verwaltung für Dav-Dav-Verbindungen (Routen, Anmeldelisten und ähnliches)                                      
	 */
	AbstractTransmitterConnections(final ServerDavParameters serverDavParameters, final LowLevelConnectionsManagerInterface lowLevelConnectionsManager, final HighLevelTransmitterManagerInterface transmitterManager) {
		_serverDavParameters = serverDavParameters;
		_lowLevelConnectionsManager = lowLevelConnectionsManager;
		_transmitterManager = transmitterManager;
	}

	/**
	 * Erstellt eine neue LowLevelCommunication mit den hinterlegten ServerDavParameters
	 * @param connection Interface zum Aufbauen von Verbindungen (z. B.  {@link de.bsvrz.dav.daf.communication.tcpCommunication.TCP_IP_Communication})
	 * @param connected Ist die Verbindung bereits aufgebaut (bei eingehenden Verbindungen)?
	 * @return neue LowLevelCommunication 
	 * @throws ConnectionException
	 */
	LowLevelCommunication createLowLevelCommunication(final ConnectionInterface connection, final boolean connected) throws ConnectionException {
		return new LowLevelCommunication(
				connection,
				_serverDavParameters.getDavCommunicationOutputBufferSize(),
				_serverDavParameters.getDavCommunicationInputBufferSize(),
				_serverDavParameters.getSendKeepAliveTimeout(),
				_serverDavParameters.getReceiveKeepAliveTimeout(),
				LowLevelCommunication.NORMAL_MODE,
				connected
		);
	}

	/**
	 * Ersteltl eine neue T_T_HighLevelCommunication
	 * @param weight Gewicht der Verbindung (für Routenberechnung)
	 * @param userName Benutzername zum Anmelden
	 * @param password Passwort zum Anmelden
	 * @param properties Verbindungsparameter 
	 * @param incomingConnection Handelt es sich um eine eingehende Verbindung? 
	 * @return neue T_T_HighLevelCommunication
	 */
	T_T_HighLevelCommunication createTransmitterHighLevelCommunication(
			final short weight, final String userName, final String password, final ServerConnectionProperties properties, final boolean incomingConnection) {

		return new T_T_HighLevelCommunication(
				properties, _transmitterManager, _lowLevelConnectionsManager, weight, false, userName, password, incomingConnection
		);
	}

}
