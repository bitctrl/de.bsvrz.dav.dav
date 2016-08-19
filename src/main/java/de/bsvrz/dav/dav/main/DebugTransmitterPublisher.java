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
import de.bsvrz.dav.daf.main.config.ConfigurationObject;
import de.bsvrz.dav.daf.main.config.DataModel;
import de.bsvrz.dav.daf.main.config.DavApplication;
import de.bsvrz.dav.daf.main.config.SystemObject;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.Set;

/**
 * Veröffentlicht den Verbindungszustand zwischen Datenverteilern auf der Konsole
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class DebugTransmitterPublisher implements TransmitterStatusPublisher {
	private static final Debug _debug = Debug.getLogger();
	private final ConfigurationObject _davObject;
	private final DataModel _dataModel;

	/** 
	 * Erstellt einen neuen DebugTransmitterPublisher
	 * @param davObject Datenverteiler-Objekt
	 */
	public DebugTransmitterPublisher(final ConfigurationObject davObject) {
		_davObject = davObject;
		_dataModel = _davObject.getDataModel();
	}

	@Override
	public void update(final Set<TransmitterStatus> connections) {
		StringBuilder builder = new StringBuilder();
		builder.append("Verbundene Datenverteiler von ").append(_davObject.getPidOrId()).append(":");
		builder.append(String.format("%n%20s | %20s | %-35s | %-35s | %-40s", "Datenverteiler", "Adresse", "Zustand", "Verschlüsselung", "Meldung"));
		builder.append(String.format("%n%20s | %20s + %-35s + %-35s + %-40s", "", "", "", "", "").replace(' ', '-'));
		for(TransmitterStatus value : connections) {
			CommunicationState state = value.getCommunicationState();
			SystemObject davApplication = _dataModel.getObject(value.getDavApplication());
			if(!(davApplication instanceof DavApplication)) {
				davApplication = null;
			}
			String address = value.getAddress();
			if(state == CommunicationState.NotRelevant) continue;
			String message = value.getMessage();
			EncryptionStatus encryptionStatus = value.getEncryptionStatus();
			builder.append(String.format("%n%20s | %20s | %-35s | %-35s | %-40s", davApplication == null ? value.getDavApplication() : davApplication.getPidOrId(), address, state, encryptionStatus, message));
		}
		_debug.info(builder.toString());
	}
}
