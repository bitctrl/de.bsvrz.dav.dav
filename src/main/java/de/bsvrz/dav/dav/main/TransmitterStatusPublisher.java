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

import java.util.Set;

/**
 * Listener-Interface für Klassen, die über Änderungen an den verbundenen Datenverteilern informiert werden
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public interface TransmitterStatusPublisher {

	/**
	 * Wird aufgerufen, wenn sich die verbundenen Datenverteiler geändert haben.
	 * Als Key werden die konfigurierten Verbindungsinformationen gespeichert
	 * (siehe {@link de.bsvrz.dav.daf.main.impl.ConfigurationManager#getTransmitterConnectionInfo(long)}).
	 * 
	 * Als Values wird der Verbindungsstatus gespeichert plus eine eventuelle Fehlernachricht.
	 * 
	 * @param connections Konfigurierte Verbindungen
	 */
	void update(Set<TransmitterStatus> connections);

}
