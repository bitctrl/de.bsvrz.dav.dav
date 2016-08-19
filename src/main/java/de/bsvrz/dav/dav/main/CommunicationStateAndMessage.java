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

import de.bsvrz.dav.daf.main.EncryptionStatus;

/**
 * Ein Kommunikationsstatus plus Fehlernachricht und Datenverteileradresse (falls bekannt)
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public final class CommunicationStateAndMessage {

	/**
	 * Verbindungszustand
	 */
	private final CommunicationState _state;

	/**
	 * Verschlüsselung
	 */
	private final EncryptionStatus _encryptionState;

	/**
	 * Adresse des Kommunikationspartners
	 */
	private final String _address;

	/**
	 * Eventuelle Fehlermeldung
	 */
	private final String _message;

	public CommunicationStateAndMessage(final String address, final CommunicationState state, final EncryptionStatus encryptionState, final String message) {
		_address = address;
		_state = state;
		_encryptionState = encryptionState;
		_message = message;
	}

	/**
	 * Gibt den Verbindungszustand zurück
	 * @return Zustand
	 */
	public CommunicationState getState() {
		return _state;
	}

	/**
	 * Gibt die Fehlermeldung zurück
	 * @return Fehlermeldung
	 */
	public String getMessage() {
		return _message;
	}

	/**
	 * Gibt die Netzwerkadresse des Kommunikationspartners zurück
	 * @return Netzwerkadresse
	 */
	public String getAddress() {
		return _address;
	}

	/**
	 * Gibt den Verschlüsselungszustand zurück
	 * @return Verschlüsselungszustand
	 */
	public EncryptionStatus getEncryptionState() {
		return _encryptionState;
	}

	@Override
	public String toString() {
		if(_message == null || _message.isEmpty()) return String.valueOf(_state);
		return _state + " (" + _message + ")";
	}

	@Override
	public boolean equals(final Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		final CommunicationStateAndMessage that = (CommunicationStateAndMessage) o;

		if(_state != that._state) return false;
		if(!_encryptionState.equals(that._encryptionState)) return false;
		if(_address != null ? !_address.equals(that._address) : that._address != null) return false;
		return !(_message != null ? !_message.equals(that._message) : that._message != null);

	}

	@Override
	public int hashCode() {
		int result = _state.hashCode();
		result = 31 * result + _encryptionState.hashCode();
		result = 31 * result + _address.hashCode();
		result = 31 * result + (_message != null ? _message.hashCode() : 0);
		return result;
	}
}
