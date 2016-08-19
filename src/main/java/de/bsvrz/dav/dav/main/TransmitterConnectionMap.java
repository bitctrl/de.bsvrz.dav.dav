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
import de.bsvrz.dav.daf.main.impl.config.telegrams.TransmitterConnectionInfo;
import de.bsvrz.dav.daf.main.impl.config.telegrams.TransmitterInfo;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunication;

import java.util.*;

/**
 * Klasse, die die offenen Verbindungen eines Datenverteilers speichert und diese den konfigurierten Verbindungen zuordnen kann, sowie den Status von Verbindungen abfragen kann.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public final class TransmitterConnectionMap {
	
	private final TransmitterConnectionInfo[] _infos;
	
	private final long _myTransmitterId;

	private final Set<Long> _disabledTransmitterConnections = new HashSet<Long>();

	private final Map<Long, T_T_HighLevelCommunication> _communicationsById = new HashMap<Long, T_T_HighLevelCommunication>();
	
	private boolean _closed = false;
	
	private final Set<TransmitterConnectionInfo> _activeExchangeConnections = new HashSet<TransmitterConnectionInfo>();

	/** 
	 * Erstellt eine neue TransmitterConnectionMap
	 * @param infos Konfigurierte Datenverteilerverbindungen
	 * @param myTransmitterId Eigene Datenverteiler-ID
	 */
	public TransmitterConnectionMap(final TransmitterConnectionInfo[] infos, final long myTransmitterId) {
		_infos = infos;
		_myTransmitterId = myTransmitterId;
	}

	/** 
	 * Gibt die eigene Datenverteiler-ID zurück
	 * @return die eigene Datenverteiler-ID
	 */
	public long getMyTransmitterId() {
		return _myTransmitterId;
	}

	/** 
	 * Gibt die Verbindung zum Datenverteiler mit der angegebenen ID zurück
	 * @param id Datenverteiler-ID
	 * @return die Verbindung zum Datenverteiler mit der angegebenen ID, oder <code>null</code> falls nicht vorhanden
	 */
	public T_T_HighLevelCommunication getConnection(final long id) {
		return _communicationsById.get(id);
	}

	/**
	 * Gibt die Verbindugn zurück, die der angegebenen konfigurierten Verbindung entspicht
	 * @param info konfigurierte Verbindung
	 * @return die Verbindung zum Datenverteiler mit der angegebenen Verbindung, oder <code>null</code> falls nicht vorhanden
	 */
	public T_T_HighLevelCommunication getConnection(final TransmitterConnectionInfo info) {
		if(info.isActiveConnection()) {
			if(isSelf(info.getTransmitter_1())) {
				T_T_HighLevelCommunication connection = getConnection(info.getTransmitter_2().getTransmitterId());
				if(connection != null && !connection.isIncomingConnection()) {
					return connection;
				}
			}
			else if(isSelf(info.getTransmitter_2())) {
				T_T_HighLevelCommunication connection = getConnection(info.getTransmitter_1().getTransmitterId());
				if(connection != null && connection.isIncomingConnection()) {
					return connection;
				}
			}
		}
		return null;
	}

	/**
	 * Hilfsmethode, die überprüft, ob das übergebene {@link TransmitterInfo}-Objekt dem eigenen Datenverteiler entspricht
	 * @param info Transmitter-Info
	 * @return true falls der Parameter der eigene Datenverteiler ist, sonst false.
	 */
	private boolean isSelf(final TransmitterInfo info) {
		return info.getTransmitterId() == _myTransmitterId;
	}

	/**
	 * Merkt sich die angegebene Verbindung unter der angegebenen ID. Die ID wird separat übergeben, da {@link T_T_HighLevelCommunication#getId()} die verbundene ID erst zurückliefert, wenn die Authentifizierung ausreichend abgeschlossen wurde.
	 * Für ausgehende Verbindungen wird der Datenverteiler mit der erwarteten ID sofort eingetragen, damit die Verbindung immer der konfigurierten Verbindung zugeordnet werden kann. (Sollte die ID später nicht der erwarteten ID entsprechen, muss das {@linkplain OutgoingTransmitterConnections#updateId(T_T_HighLevelCommunication) korrigiert werden}.
	 * Besteht berets eine Verbindugn mti dieser ID, wird diese überschrieben.
	 * @param id ID
	 * @param communication Verbindung
	 */
	public void putConnection(final long id, final T_T_HighLevelCommunication communication) {
		if(id == -1) {
			throw new IllegalArgumentException();
		}
		_communicationsById.put(id, communication);
	}

	/**
	 * Entfernt eine Verbindung (zum Beispiel weil diese geschlossen wurde)
	 * @param id ID der Verbindung
	 * @param transmitterCommunication zu entfernende Verbindung unter dieser ID (wenn die gespeicherte Verbindung unter dieser ID nicht dem parameter entspicht tut diese Methode nichts. Damit wird verhindert, dass die falsche Verbindung entfernt wird)
	 */
	public void removeConnection(final long id, final T_T_HighLevelCommunication transmitterCommunication) {
		if(getConnection(id) == transmitterCommunication) {
			_communicationsById.remove(id);
		}
	}

	/**
	 * Bestimmt die konfigurierte Verbindung zu einer aktiven Verbindung
	 * @param transmitterCommunication aktive Verbindung
	 * @return  konfigurierte Verbindung (oder null, falls keine konfigurierte Entsprechung gefunden wurde) 
	 */
	public TransmitterConnectionInfo getInfo(final T_T_HighLevelCommunication transmitterCommunication) {
		return transmitterCommunication.isIncomingConnection()
				? getRemoteTransmitterConnectionInfo(transmitterCommunication.getId())
				: getTransmitterConnectionInfo(transmitterCommunication.getId());
	}

	/**
	 * Bestimmt die konfigurierte Verbindung zu einer Datenverteiler-ID
	 * @param connectedTransmitterId ID eines Datenverteilers
	 * @return  konfigurierte Verbindung zum Verbindungsaufbau dieses Datenverteilers mit dem übergebenen Datenverteiler (oder null, falls keine konfigurierte Entsprechung gefunden wurde) 
	 */
	public TransmitterConnectionInfo getInfo(final long connectedTransmitterId) {
		TransmitterConnectionInfo a = getTransmitterConnectionInfo(connectedTransmitterId);
		if(a != null) return a;
		
		TransmitterConnectionInfo b = getRemoteTransmitterConnectionInfo(connectedTransmitterId);
		return b;
	}

	/**
	 * Bestimmt die Verbindungsinformationen für eine Verbindung von diesem Datenverteiler zum angegebenen Datenverteiler.
	 *
	 * @param connectedTransmitterId ID des DAV
	 * @return Verbindungsinformationen
	 */
	public TransmitterConnectionInfo getTransmitterConnectionInfo(final long connectedTransmitterId) {
		for(final TransmitterConnectionInfo _transmitterConnectionInfo : getAllInfos()) {
			final TransmitterInfo t1 = _transmitterConnectionInfo.getTransmitter_1();
			final TransmitterInfo t2 = _transmitterConnectionInfo.getTransmitter_2();
			if(t1.getTransmitterId() == _myTransmitterId && t2.getTransmitterId() == connectedTransmitterId) {
				return _transmitterConnectionInfo;
			}
		}
		return null;
	}

	/**
	 * Bestimmt die Verbindungsinformationen für eine Verbindung vom angegebenen Datenverteiler zu diesem Datenverteiler.
	 *
	 * @param connectedTransmitterId ID des DAV
	 * @return Verbindungsinformationen
	 */
	public TransmitterConnectionInfo getRemoteTransmitterConnectionInfo(final long connectedTransmitterId) {
		for(final TransmitterConnectionInfo _transmitterConnectionInfo : getAllInfos()) {
			final TransmitterInfo t1 = _transmitterConnectionInfo.getTransmitter_1();
			final TransmitterInfo t2 = _transmitterConnectionInfo.getTransmitter_2();
			if(t2.getTransmitterId() == _myTransmitterId && t1.getTransmitterId() == connectedTransmitterId) {
				return _transmitterConnectionInfo;
			}
		}
		return null;
	}

	/**
	 * Gibt den Verbindungszustand einer konfigurierten Verbindung zurück
	 * @param info konfigurierte Verbindung
	 * @return Status
	 */
	public CommunicationStateAndMessage getState(final TransmitterConnectionInfo info) {
		T_T_HighLevelCommunication communication = getConnection(info);
		CommunicationState state = CommunicationState.NotConnected;
		String message = "";
		String address = "";
		EncryptionStatus encryptionState = EncryptionStatus.notEncrypted();
		if(communication != null) {
			CommunicationStateAndMessage s = communication.getState();
			state = s.getState();
			message = s.getMessage();
			address = communication.getRemoteAdress() + ":" + communication.getRemoteSubadress();
			encryptionState = communication.getEncryptionStatus();
		}
		if(state == CommunicationState.NotConnected) {
			encryptionState = EncryptionStatus.notEncrypted();
			if(!isSelf(info.getTransmitter_1()) && !isSelf(info.getTransmitter_2())) {
				state = CommunicationState.NotRelevant;
			}
			else if(isSelf(info.getTransmitter_2())) {
				state = CommunicationState.Listening;
			}
			else if(info.isExchangeConnection() && !_activeExchangeConnections.contains(info)) {
				state = CommunicationState.UnusedReplacementConnection;
			}
		}
		return new CommunicationStateAndMessage(address, state, encryptionState, message);
	}

	/**
	 * Gibt den Verbindungszustand mit dem übergebenen Datenverteiler zurück
	 * @param transmitterId Datenverteiler
	 * @return Status
	 */
	public CommunicationStateAndMessage getState(final long transmitterId) {
		TransmitterConnectionInfo info = getInfo(transmitterId);
		if(info != null) {
			return getState(info);
		}
		T_T_HighLevelCommunication communication = getConnection(transmitterId);
		CommunicationState state = CommunicationState.NotRelevant;
		String message = "";
		String address = "";
		EncryptionStatus encryptionStatus = EncryptionStatus.notEncrypted();
		if(communication != null) {
			CommunicationStateAndMessage s = communication.getState();
			state = s.getState();
			message = s.getMessage();
			address = communication.getRemoteAdress() + ":" + communication.getRemoteSubadress();
			encryptionStatus = communication.getEncryptionStatus();
		}
		return new CommunicationStateAndMessage(address, state, encryptionStatus, message);
	}

	/** 
	 * Gibt alle bekannten (typischerweise aktiven) Verbindungen zurück
	 * @return alle aktiven Verbindungen
	 */
	public Collection<T_T_HighLevelCommunication> getAllConnections() {
		return _communicationsById.values();
	}

	/** 
	 * Gibt alle konfigurierten Verbindungen zurück
	 * @return alle konfigurierten Verbindungen
	 */
	public Collection<TransmitterConnectionInfo> getAllInfos() {
		return Arrays.asList(_infos);
	}

	/** 
	 * Gibt <tt>true</tt> zurück, wenn die Verbindung mit dem übergebenen Datenverteiler deaktiviert ist
	 * @param transmitterId Datenverteiler-ID
	 * @return <tt>true</tt>, wenn die Verbindung mit dem übergebenen Datenverteiler deaktiviert ist, sonst <tt>false</tt>
	 */
	public boolean isDisabled(final long transmitterId) {
		return _closed || _disabledTransmitterConnections.contains(transmitterId);
	}

	/** 
	 * Gibt die Menge mit den deaktivierten Datenverteilern zurück
	 * @return die Menge mit den deaktivierten Datenverteilern
	 */
	public Set<Long> getDisabledConnections() {
		return _disabledTransmitterConnections;
	}

	/**
	 * Gibt die Menge mit aktuell "benötigten" Ersatzverbindungen zurück
	 * (unabhängig davon, ob diese aufgebaut werden konnten oder nicht) 
	 * @return Menge mit benötigten/aktivierten Ersatzverbindungen
	 */
	public Set<TransmitterConnectionInfo> getActiveExchangeConnections() {
		return _activeExchangeConnections;
	}

	/**
	 * Verhindert, dass weitere Verbindungen aufgebaut werden (markiert alle Verbindungen als deaktiviert)
	 */
	public void close() {
		_closed = true;
	}
}
