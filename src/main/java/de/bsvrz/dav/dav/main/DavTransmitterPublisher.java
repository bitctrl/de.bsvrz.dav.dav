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

import de.bsvrz.dav.daf.main.*;
import de.bsvrz.dav.daf.main.config.*;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.Collections;
import java.util.Set;

/**
 * Veröffentlicht den Kommunikationszustand ver verbundenen Datenverteiler über die Attributgruppe 
 * atg.datenverteilerKommunikationsZustand am Datenverteilerobjekt. Falls das Datenmodell zu alt ist,
 * tut diese Klasse nichts.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class DavTransmitterPublisher implements TransmitterStatusPublisher {
	private static final Debug _debug = Debug.getLogger();
	private final ClientDavConnection _connection;
	private final ConfigurationObject _davObject;
	private final DataModel _dataModel;
	private final Sender _sender;
	private Set<TransmitterStatus> _connections = Collections.emptySet();
	private DataDescription _dataDescription = null;

	/**
	 * Erstellt einen neuen DavTransmitterPublisher
	 * @param connection Verbindung
	 * @param davObject Datenverteiler-Objekt (Wichtig: kann unterschiedlich von connection.getLocalDav() sein, muss daher separat übergeben werden!) 
	 */
	public DavTransmitterPublisher(final ClientDavConnection connection, final ConfigurationObject davObject) {
		_connection = connection;
		_davObject = davObject;
		_dataModel = _connection.getDataModel();
		AttributeGroup atg = _dataModel.getAttributeGroup("atg.datenverteilerKommunikationsZustand");
		Aspect asp = _dataModel.getAspect("asp.standard");
		_sender = new Sender();
		if(atg == null || asp == null) return;
		_dataDescription = new DataDescription(atg, asp);
		try {
			_connection.subscribeSender(_sender, _davObject, _dataDescription, SenderRole.source());
		}
		catch(OneSubscriptionPerSendData oneSubscriptionPerSendData) {
			// Sollte nicht passieren
			_debug.warning("Quellanmeldung auf atg.datenverteilerKommunikationsZustand fehlgeschlagen", oneSubscriptionPerSendData);
		}
	}


	@Override
	public void update(final Set<TransmitterStatus> connections) {
		_connections = connections;
		_sender.triggerSender();
	}

	private Data createData(final Set<TransmitterStatus> connections) {
		Data data = _connection.createData(_dataDescription.getAttributeGroup());
		Data.Array array = data.getArray("KommunikationsZustand");
		array.setLength(connections.size());
		int i = 0;
		for(TransmitterStatus status : connections) {
			CommunicationState communicationState = status.getCommunicationState();
			if(communicationState == CommunicationState.NotRelevant) continue;
			Data item = array.getItem(i++);
			SystemObject dav = _dataModel.getObject(status.getDavApplication());
			item.getReferenceValue("Datenverteiler").setSystemObject(dav);
			item.getTextValue("Adresse").setText(status.getAddress());
			item.getTextValue("Zustand").setText(communicationState.toString());
			item.getTextValue("Meldung").setText(status.getMessage());
		}
		array.setLength(i);
		return data;
	}

	private class Sender implements ClientSenderInterface {

		private byte _state = -1;

		@Override
		public void dataRequest(final SystemObject object, final DataDescription dataDescription, final byte state) {
			_state = state;
			triggerSender();
		}

		public void triggerSender() {
			if(_dataDescription == null) return;
			if(_state == START_SENDING){
				try {
					_connection.sendData(new ResultData(_davObject, _dataDescription, _connection.getTime(), createData(_connections)));
				}
				catch(SendSubscriptionNotConfirmed sendSubscriptionNotConfirmed) {
					_debug.fine("Kann Datensatz nicht senden", sendSubscriptionNotConfirmed);
				}
			}
		}
		@Override
		public boolean isRequestSupported(final SystemObject object, final DataDescription dataDescription) {
			return true;
		}

	}
}
