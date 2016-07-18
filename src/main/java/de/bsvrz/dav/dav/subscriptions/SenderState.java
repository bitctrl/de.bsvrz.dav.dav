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

package de.bsvrz.dav.dav.subscriptions;

/**
 * Status eines Senders/einer Quelle
 *
 * @author Kappich Systemberatung
 * @version $Revision: 11461 $
 */
public enum SenderState {
	/** Unbekannt, Sender wurde gerade erst angemeldet */
	UNKNOWN(false),
	/** Empfänger sind Verfügbar, bereit zum Senden */
	RECEIVERS_AVAILABLE(true),
	/** Keine Empfänger verfügbar */
	NO_RECEIVERS(true),
	/** Es ist unbekannt, ob Empfänger verfügbar sind */
	WAITING(true),
	/** Keine Rechte zum Senden */
	NOT_ALLOWED(false),
	/** ungültige Anmeldung (z.B. mehrere Quellen oder Senken) */
	INVALID_SUBSCRIPTION(false),
	/** ungültiger Status einer entfernten Anmeldung, z.B. keine Rechte am entfernten Dav oder nicht verantwortlich */
	NO_REMOTE_SOURCE(false),
	/** es gibt mehrere mögliche Zentraldatenverteiler, Anmeldung daher deaktiviert */
	MULTIPLE_REMOTE_LOCK(false);

	private final boolean _validSender;

	private SenderState(final boolean validSender) {
		_validSender = validSender;
	}

	/**
	 * Gibt zurück, ob der Sender gültig ist
	 * @return true wenn die Anmeldung gültig ist damit z.B. Empfängern mitgeteilt werden kann, dass es Sender gibt.
	 */
	public boolean isValidSender() {
		return _validSender;
	}
}
