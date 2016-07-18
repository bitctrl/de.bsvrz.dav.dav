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

/**
 * Mögliche Statuswerte für eine Verbindung zwischen 2 Datenverteilern (aus der Sicht eines der beiden Datenverteiler)
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public enum CommunicationState {

	/**
	 * Verbindung ist nicht verbunden (Der Datenverteiler wartet auf neuen Verbindungsversuch)
	 */
	NotConnected("Nicht verbunden"),
	/**
	 * Verbindung ist nicht verbunden (Es handelt sich um eine Ersatzverbindung, welche nicht erforderlich ist)
	 */
	UnusedReplacementConnection("Ersatzverbindung (nicht verbunden)"),
	/**
	 * Verbindung ist nicht verbunden, da sie deaktiviert wurde
	 */
	Disabled("Deaktiviert (nicht verbunden)"),
	/**
	 * Der aktuelle Datenverteiler ist nicht in die Verbindung involviert
	 */
	NotRelevant("Keine konfigurierte Verbindung"),
	/**
	 * Es wird auf Verbindungaufbau durch den anderen Datenverteiler gewartet
	 */
	Listening("Warte auf eingehende Verbindung"),
	/**
	 * Wie NotConnected, aber eine bestehende Verbindung wurde durch einen Fehler terminiert
	 */
	Error("Fehler"),
	/**
	 * Die Verbindung wird gerade aufgebaut
	 */
	Connecting("Verbindungsaufbau"),
	/**
	 * Der Datenverteiler authentifiziert sich beim Remote-Datenverteiler
	 */
	Authenticating("Authentifizierung"),
	/**
	 * Die Verbindung wurde hergestellt und ist aktiv
	 */
	Connected("Verbunden"),
	/**
	 * Die Verbindung wird gerade geschlossen und bestehende Anmeldungen werden abgemeldet
	 * (zum Beispiel Aufgrund eines Fehlers oder weil ein Terminierungstelegramm gesendet wurde)
	 */
	Disconnecting("Verbindungsabbau");

	private final String _localizedString;

	CommunicationState(String localizedString) {
		_localizedString = localizedString;
	}

	@Override
	public String toString() {
		return _localizedString;
	}
}
