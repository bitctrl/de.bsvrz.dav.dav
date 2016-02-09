/*
 * Copyright 2011 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.dav.dav.
 * 
 * de.bsvrz.dav.dav is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.dav.dav is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.dav.dav; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.bsvrz.dav.dav.subscriptions;

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.BaseSubscriptionInfo;
import de.bsvrz.dav.dav.main.ConnectionState;

/**
 * Interface f�r eine Anmeldung an einer Datenidentifikation-Objekt-Kombination
 * @author Kappich Systemberatung
 * @version $Revision: 11454 $
 */
public interface Subscription {

	/**
	 * Gibt die Schnittstelle zum Kommunikationspartner zur�ck, also z.B. eine T_A oder T_T-Verbindung
	 * @return die Schnittstelle zum Kommunikationspartner
	 */
	CommunicationInterface getCommunication();

	/**
	 * Gibt die Objekt-Attributgruppenverwendung-Simulationsvariante-Kombination zur�ck, auf die diese Anmeldung stattfindet
	 * @return BaseSubscriptionInfo
	 */
	public BaseSubscriptionInfo getBaseSubscriptionInfo();

	/**
	 * Gibt zur�ck, ob die Anmeldung von der lokalen Rechtepr�fung erlaubt ist
	 * @return
	 */
	boolean isAllowed();

	/**
	 * Gibt die Id des angemeldeten Benutzers zur�ck. Liefert das gleiche wie getCommunication().getRemoteUserId()
	 * @return Id den angemeldeten Benutzers
	 */
	public long getUserId();

	/**
	 * Liefert die Id der anmeldenden Applikation bzw. des anmeldenden Datenverteilers (nicht zwingend der direkt verbundene Datenverteiler, sondern
	 * der Kommunikationspartner, der die Daten bereitstellt oder empf�ngt)
	 * @return Id der Applikation (bei T_A-Verbindungen) oder Id des Datenverteilers (bei T_T-Verbindungen)
	 */
	public long getNodeId();

	/**
	 * Gibt den Verbindungsstatus zum Kommunikationspartner zur�ck
	 * @return Verbindugnsstatus
	 */
	ConnectionState getConnectionState();

	/**
	 * Gibt die Zentraldatenverteiler-ID zur�ck. Nur sinnvoll bei Quelle/Senke-Anmeldungen. Ist der Zentraldatenverteiler unbekannt, wird -1 zur�ckgegeben.
	 * @return die Zentraldatenverteiler-ID
	 */
	long getCentralDistributorId();

	/**
	 * Beendet die Datenanmeldung, sendet eventuelle Abmeldetelegramme
	 */
	void unsubscribe();
}
