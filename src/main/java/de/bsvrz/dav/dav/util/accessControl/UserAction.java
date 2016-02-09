/*
 * Copyright 2010 by Kappich Systemberatung Aachen
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

package de.bsvrz.dav.dav.util.accessControl;

/**
 * Steht f�r eine Aktion, die ein Benutzer durchf�hren kann, und f�r die z.B. ExtendedUserInfo �berpr�fen soll, ob sie erlaubt ist
 *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
public enum UserAction {

	RECEIVER("Empf�nger"),    // Daten lesen
	SENDER("Sender"),         // Daten schreiben
	SOURCE("Quelle"),         // Daten als Quelle anmelden
	DRAIN("Senke")            // Daten als Senke anmelden
	;
	private final String _text;

	private UserAction(final String text) {
		_text = text;
	}

	@Override
	public String toString() {
		return _text;
	}
}
