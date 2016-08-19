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
import java.util.HashSet;
import java.util.Set;

/**
 * Empfänger für die Parameter-Attributegruppe "atg.deaktivierteVerbindungen" mit der Dav-Dav-Verbindungen temporär deaktiviert werden können
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class DisabledTransmitterConnectionsReceiver {

	private static final Debug _debug = Debug.getLogger();

	private final LowLevelConnectionsManagerInterface _lowLevelConnectionsManager;
	private final ClientDavConnection _connection;
	private final ConfigurationObject _davObject;
	private final DataModel _dataModel;
	private DataDescription _ddReceive = null;
	private DataDescription _ddSend = null;

	public DisabledTransmitterConnectionsReceiver(final LowLevelConnectionsManagerInterface lowLevelConnectionsManager, final ClientDavConnection connection, final ConfigurationObject davObject) {
		_lowLevelConnectionsManager = lowLevelConnectionsManager;
		_connection = connection;
		_davObject = davObject;
		_dataModel = _connection.getDataModel();
		AttributeGroup atg = _dataModel.getAttributeGroup("atg.deaktivierteVerbindungen");
		Aspect aspReceive = _dataModel.getAspect("asp.parameterSoll");
		Aspect aspSend = _dataModel.getAspect("asp.parameterIst");
		if(atg == null || aspReceive == null || aspSend == null) return;
		_ddReceive = new DataDescription(atg, aspReceive);
		_ddSend = new DataDescription(atg, aspSend);
		ParamHandler paramHandler = new ParamHandler();
		try {
			_connection.subscribeSender(paramHandler, _davObject, _ddSend, SenderRole.source());
		}
		catch(OneSubscriptionPerSendData oneSubscriptionPerSendData) {
			_debug.warning("Quellanmeldung auf atg.deaktivierteVerbindungen fehlgeschlagen", oneSubscriptionPerSendData);			
		}
		_connection.subscribeReceiver(paramHandler, _davObject, _ddReceive, ReceiveOptions.normal(), ReceiverRole.receiver());
	}

	private class ParamHandler implements ClientSenderInterface, ClientReceiverInterface{
		
		private volatile byte _sendingState = -1;
		private volatile Data _lastData = null;

		@Override
		public void update(final ResultData[] results) {
			for(ResultData result : results) {
				handleData(result.getData());
			}
		}

		private void handleData(final Data data) {
			if(data == null){
				_debug.fine("Empfange leeren Parameterdatensatz atg.deaktivierteVerbindungen");
			}
			else {
				_debug.fine("Empfange Parameterdatensatz atg.deaktivierteVerbindungen");
			}
			processData(data);
			_lastData = data;
			if(_sendingState == START_SENDING) {
				try {
					_connection.sendData(new ResultData(_davObject, _ddSend, _connection.getTime(), data));
				}
				catch(SendSubscriptionNotConfirmed sendSubscriptionNotConfirmed) {
					_debug.fine("Kann Datensatz nicht senden", sendSubscriptionNotConfirmed);
				}
			}
		}

		private void processData(final Data data) {
			final Set<Long> disabledConnections = getConnections(data);
			_debug.fine("Neue Menge mit deaktivierten Datenverteilern", disabledConnections);
			_lowLevelConnectionsManager.setDisabledTransmitterConnections(disabledConnections);
		}

		private Set<Long> getConnections(final Data data) {
			if(data == null) return Collections.emptySet();
			HashSet<Long> result = new HashSet<Long>();
			for(Data subData : data.getItem("DeaktivierteVerbindung")) {
				SystemObject transmitter = subData.getReferenceValue("RemoteDatenverteiler").getSystemObject();
				String text = subData.getTextValue("VerbindungTrennen").getText();
				if(transmitter != null && "Ja".equals(text)){
					result.add(transmitter.getId());
				}
			}
			return result;
		}

		@Override
		public void dataRequest(final SystemObject object, final DataDescription dataDescription, final byte state) {
			_sendingState = state;
			if(state == START_SENDING){
				handleData(_lastData);
			}
		}

		@Override
		public boolean isRequestSupported(final SystemObject object, final DataDescription dataDescription) {
			return true;
		}
	}
}
