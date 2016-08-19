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

import java.util.Objects;

/**
 * Verbindungszustand eines Datenverteilers
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public final class TransmitterStatus implements Comparable<TransmitterStatus> {
	
	private final long _transmitterId;
	
	private final String _address;
	
	private final CommunicationState _communicationState;
	
	private final EncryptionStatus _encryptionStatus;
	
	private final String _message;

	public TransmitterStatus(final long davApplication, final String address, final CommunicationState communicationState, final String message, final EncryptionStatus encryptionStatus) {
		if(address == null) throw new IllegalArgumentException("address ist null");
		Objects.requireNonNull(encryptionStatus, "encryptionStatus == null");
		_encryptionStatus = encryptionStatus;
		_transmitterId = davApplication;
		_address = address;
		_communicationState = communicationState;
		_message = message == null ? "" : message;
	}

	/** 
	 * Gibt die Datenverteiler-ID zurück
	 * @return die Datenverteiler-ID
	 */
	public long getDavApplication() {
		return _transmitterId;
	}

	/** 
	 * Gibt die Adresse des verbundenen Datenverteilers zurück, oder einen Leerstring wenn der Datenverteiler nicht verbunden ist.
	 * @return die Adresse des verbundenen Datenverteilers (oder Leerstring falls nicht verfügbar, nicht null)
	 */
	public String getAddress() {
		return _address;
	}

	/**
	 * Gibt den Verbindugnsstatus zum Datenverteiler zurück
	 * @return Verbindungszustand (nicht null)
	 */
	public CommunicationState getCommunicationState() {
		return _communicationState;
	}

	/** 
	 * Gibt eine eventuelle Fehlernachricht zurück
	 * @return Fehlernachricht oder leerstring (nicht null)
	 */
	public String getMessage() {
		return _message;
	}

	/** 
	 * Gibt die ID des Datenverteilers zurück
	 * @return die ID des Datenverteilers
	 */
	public long getTransmitterId() {
		return _transmitterId;
	}

	/** 
	 * Gibt den Verschlüsselungszustand zurück
	 * @return den Verschlüsselungszustand
	 */
	public EncryptionStatus getEncryptionStatus() {
		return _encryptionStatus;
	}

	@Override
	public boolean equals(final Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		final TransmitterStatus that = (TransmitterStatus) o;

		if(_transmitterId != that._transmitterId) return false;
		if(!_address.equals(that._address)) return false;
		if(_communicationState != that._communicationState) return false;
		if(!_encryptionStatus.equals(that._encryptionStatus)) return false;
		return _message.equals(that._message);

	}

	@Override
	public int hashCode() {
		int result = (int) (_transmitterId ^ (_transmitterId >>> 32));
		result = 31 * result + _address.hashCode();
		result = 31 * result + _communicationState.hashCode();
		result = 31 * result + _encryptionStatus.hashCode();
		result = 31 * result + _message.hashCode();
		return result;
	}

	@Override
	public int compareTo(final TransmitterStatus o) {
		return (_transmitterId < o._transmitterId) ? -1 : ((_transmitterId == o._transmitterId) ? 0 : 1);
	}
}
