/*
 * Copyright 2013 by Kappich Systemberatung Aachen
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

package de.bsvrz.dav.dav.subscriptions;

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.ApplicationDataTelegram;
import de.bsvrz.dav.daf.communication.protocol.UserLogin;

/**
 * Basis-Interface für eine Netzwerkverbindung Dav-Dav oder Dav-App
 *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
public interface CommunicationInterface {

	/**
	 * Gibt den Authentifizierungsstatus des verbundenen Benutzers zurück
	 * @return der verbundene Benutzer
	 */
	UserLogin getUserLogin();

	/**
	 * Versendet ein Daten-Telegramm über diese Verbindung
	 * @param telegram Telegramm
	 * @param toCentralDistributor
	 * true: In Richtung des Zentraldatenverteilers, beim Sender-Senke-Datenfluss.
	 * false: Aus Richtung des Zentraldatenverteilers, beim Quelle-Empfänger-Datenfluss.
	 */
	void sendData(ApplicationDataTelegram telegram, boolean toCentralDistributor);

	/**
	 * Gibt die Id der Verbindung bzw. des Kommunikationspartners zurück.
	 * <ul>
	 *     <li>Bei Applikationen: Applikations-Id</li>
	 *     <li>Bei Datenverteilern: Datenverteiler-Id</li>
	 * </ul>
	 * @return Id
	 */
	long getId();
}
