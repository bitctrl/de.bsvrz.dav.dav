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

/**
 * Status eines Senders/einer Quelle
 *
 * @author Kappich Systemberatung
 * @version $Revision: 11461 $
 */
public enum SenderState {
	/** Unbekannt, Sender wurde gerade erst angemeldet */
	UNKNOWN(false),
	/** Empf�nger sind Verf�gbar, bereit zum Senden */
	RECEIVERS_AVAILABLE(true),
	/** Keine Empf�nger verf�gbar */
	NO_RECEIVERS(true),
	/** Es ist unbekannt, ob Empf�nger verf�gbar sind */
	WAITING(true),
	/** Keine Rechte zum Senden */
	NOT_ALLOWED(false),
	/** ung�ltige Anmeldung (z.B. mehrere Quellen oder Senken) */
	INVALID_SUBSCRIPTION(false),
	/** ung�ltiger Status einer entfernten Anmeldung, z.B. keine Rechte am entfernten Dav oder nicht verantwortlich */
	NO_REMOTE_SOURCE(false),
	/** es gibt mehrere m�gliche Zentraldatenverteiler, Anmeldung daher deaktiviert */
	MULTIPLE_REMOTE_LOCK(false);

	private final boolean _validSender;

	private SenderState(final boolean validSender) {
		_validSender = validSender;
	}

	/**
	 * Gibt zur�ck, ob der Sender g�ltig ist
	 * @return true wenn die Anmeldung g�ltig ist damit z.B. Empf�ngern mitgeteilt werden kann, dass es Sender gibt.
	 */
	public boolean isValidSender() {
		return _validSender;
	}
}
