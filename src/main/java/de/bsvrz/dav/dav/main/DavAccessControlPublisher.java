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

/**
 * Veröffentlicht den Status der Rechteprüfung über die Attributgruppe 
 * atg.datenverteilerRechteprüfung am Datenverteilerobjekt. Falls das Datenmodell zu alt ist,
 * tut diese Klasse nichts.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class DavAccessControlPublisher {
	private static final Debug _debug = Debug.getLogger();
	private final ClientDavConnection _connection;
	private final ConfigurationObject _davObject;
	private final DataModel _dataModel;
	private final Sender _sender;
	private ResultData _data;
	private DataDescription _dataDescription = null;

	/**
	 * Erstellt einen neuen DavTransmitterPublisher
	 * @param connection Verbindung
	 * @param davObject Datenverteiler-Objekt (Wichtig: kann unterschiedlich von connection.getLocalDav() sein, muss daher separat übergeben werden!) 
	 * @param userRightsChecking Art der Rechteprüfung, die veröffentlicht werden soll                     
	 */
	public DavAccessControlPublisher(final ClientDavConnection connection, final ConfigurationObject davObject, final ServerDavParameters.UserRightsChecking userRightsChecking) {
		_connection = connection;
		_davObject = davObject;
		_dataModel = _connection.getDataModel();
		AttributeGroup atg = _dataModel.getAttributeGroup("atg.datenverteilerRechteprüfung");
		Aspect asp = _dataModel.getAspect("asp.standard");
		_sender = new Sender();
		if(atg == null || asp == null) return;
		_dataDescription = new DataDescription(atg, asp);
		_data = new ResultData(_davObject, _dataDescription, _connection.getTime(), createData(userRightsChecking));
		try {
			_connection.subscribeSource(_sender, _data);
		}
		catch(OneSubscriptionPerSendData oneSubscriptionPerSendData) {
			// Sollte nicht passieren
			_debug.warning("Quellanmeldung auf atg.datenverteilerKommunikationsZustand fehlgeschlagen", oneSubscriptionPerSendData);
		}
	}

	private Data createData(final ServerDavParameters.UserRightsChecking userRightsChecking) {
		Data data = _connection.createData(_dataDescription.getAttributeGroup());
		switch(userRightsChecking){
			case Disabled:
				data.getTextValue("Rechteprüfung").setText("Deaktiviert");
				break;
			case Compatibility_Enabled: // Fallthrough, Compatibility_Enabled sollte hier so interpretiert werden wie in de.bsvrz.dav.dav.main.HighLevelSubscriptionsManager.setConfigurationAvailable()
			case OldDataModel:
				data.getTextValue("Rechteprüfung").setText("Alte Rechteprüfung");
				break;
			case NewDataModel:
				data.getTextValue("Rechteprüfung").setText("Neue Rechteprüfung");
				break;
		}
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
					_connection.sendData(_data);
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
