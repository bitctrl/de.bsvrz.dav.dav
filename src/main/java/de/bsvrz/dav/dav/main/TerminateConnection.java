/*
  * Copyright 2010 by Kappich
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
import de.bsvrz.dav.daf.main.config.Aspect;
import de.bsvrz.dav.daf.main.config.AttributeGroup;
import de.bsvrz.dav.daf.main.config.DataModel;
import de.bsvrz.dav.daf.main.config.SystemObject;
import de.bsvrz.sys.funclib.application.StandardApplication;
import de.bsvrz.sys.funclib.application.StandardApplicationRunner;
import de.bsvrz.sys.funclib.commandLineArgs.ArgumentList;
import de.bsvrz.sys.funclib.configObjectAcquisition.ConfigurationHelper;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stoppt die Datenverteiler- bzw. Applikations-Verbindung mit der übergebenen Objekt-Spezifikation.
 * -objekt ID oder IDs durch Kommata getrennt, ist die Angabe welche Prozesse terminiert werden sollen.
 * Bsp : "-objekt=1466766103639706224 -benutzer=Tester -authentifizierung=passwd -debugLevelStdErrText=INFO"
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class TerminateConnection implements StandardApplication {

	private static Debug _debug = Debug.getLogger();
	private String _objectSpec;
	private String _appName;
	private long _delayMillis;

	public static void main(String[] args) {
		StandardApplicationRunner.run(new TerminateConnection(), args);
	}

	@Override
	public void parseArguments(ArgumentList argumentList) throws Exception {
		_debug = Debug.getLogger();
		_objectSpec = argumentList.fetchArgument("-objekt=").asString();
		_appName = argumentList.fetchArgument("-name=").asString();
		_delayMillis = argumentList.fetchArgument("-wartezeit=jetzt").asRelativeTime();
	}

	@Override
	public void initialize(ClientDavInterface connection) throws Exception {
		List<SystemObject> objects = new ArrayList<SystemObject>();
		if(_objectSpec.length() > 0) {
			objects.addAll(ConfigurationHelper.getObjects(_objectSpec, connection.getDataModel()));
		}
		if(_appName.length() > 0){
			for(String app : _appName.split(":")) {
				DataModel dataModel = connection.getDataModel();
				for(SystemObject systemObject : dataModel.getType("typ.datenverteiler").getElements()) {
					if(matches(systemObject, app)){
						objects.add(systemObject);
					}
				}
				for(SystemObject systemObject : dataModel.getType("typ.applikation").getElements()) {
					if(matches(systemObject, app)){
						objects.add(systemObject);
					}
				}
			}
		}
		if(objects.isEmpty()){
			_debug.warning("Keine Objekte ausgewählt. Mit -objekt=... Pids und IDs auswählen, oder mit -name=... Applikationsnamen auswählen");
		}
		sendTerminationData(connection, objects, _delayMillis);
		connection.disconnect(false, "");
	}

	private static boolean matches(final SystemObject systemObject, final String appName) {
		if(systemObject.getName().startsWith(appName)){
			if(systemObject.getName().length() == appName.length()){
				return true;
			}
			if(systemObject.getName().startsWith(appName + ": ")){
				return true;
			}
		}
		return false;
	}

	public static void sendTerminationData(final ClientDavInterface connection, final List<SystemObject> systemObjectList, final long delayMillis) throws InterruptedException {
		if(delayMillis <= 0){
			sendTerminationData(connection, systemObjectList);
			return;
		}
		for(Iterator<SystemObject> iterator = systemObjectList.iterator(); iterator.hasNext(); ) {
			final SystemObject systemObject = iterator.next();
			sendTerminationData(connection, Collections.singletonList(systemObject));
			if(iterator.hasNext()){
				Thread.sleep(delayMillis);
			}
		}
	}
	
	public static void sendTerminationData(final ClientDavInterface connection, final List<SystemObject> systemObjectList) throws InterruptedException {
		if(_debug == null){
			_debug = Debug.getLogger();
		}
		DataModel configuration = connection.getDataModel();
		SystemObject object = connection.getLocalDav();

		_debug.info("Terminiere", systemObjectList);
		AttributeGroup atg = configuration.getAttributeGroup("atg.terminierung");
		_debug.fine("Attributgruppe: " + atg);
		if(atg == null){
			throw new IllegalStateException("Aktualisieren Sie die Konfiguration der Kernsoftware.\nDie benötigte Attributgruppe (atg.terminierung) ist unbekannt.");
		}

		Aspect aspect = configuration.getAspect("asp.anfrage");
		_debug.fine("Aspekt: " + aspect);
		if(aspect == null){
			throw new IllegalStateException("Aktualisieren Sie die Konfiguration der Kernsoftware.\nDer benötigte Aspekt (asp.anfrage) ist unbekannt.");
		}


		Data data = connection.createData(atg);
		Data.ReferenceArray referenceArrayApplication = data.getReferenceArray("Applikationen");
		referenceArrayApplication.setLength(systemObjectList.size());

		Data.ReferenceArray referenceArrayDataDistributor = data.getReferenceArray("Datenverteiler");
		referenceArrayDataDistributor.setLength(systemObjectList.size());

		int application = 0;
		int dataDistributor = 0;

		for(SystemObject systemObject : systemObjectList) {
			if(systemObject == null){
				_debug.error("Die Übergebene Objekt-Spezifikation ist ungültig.");
				continue;
			}

			if(systemObject.isOfType("typ.datenverteiler")){
				Data.ReferenceValue referenceValue = referenceArrayDataDistributor.getReferenceValue(dataDistributor++);
				referenceValue.setSystemObject(systemObject);
			}else if(systemObject.isOfType("typ.applikation")){
				Data.ReferenceValue referenceValue = referenceArrayApplication.getReferenceValue(application++);
				referenceValue.setSystemObject(systemObject);
			}else{
				_debug.error("Das Objekt " + systemObject + " wurde ignoriert, ein es keine Applikation oder Datenverteiler ist.");
			}
		}

		referenceArrayApplication.setLength(application);
		referenceArrayDataDistributor.setLength(dataDistributor);

		DataDescription dataDescription = new DataDescription(atg, aspect);
		ResultData dataTel = new ResultData(object, dataDescription, System.currentTimeMillis(), data);

		final Sender sender = new Sender(connection, dataTel);
		try {
			connection.subscribeSender(sender, object, dataDescription, SenderRole.sender());
		}
		catch(Exception e) {
			System.out.println("Fehler: " + e.getMessage());
		}
		sender.await();
	}

	private static class Sender implements ClientSenderInterface {

		private final ClientDavInterface _connection;
		private final ResultData _data;
		private final Condition _send;
		private final ReentrantLock _lock;
		private boolean _hasSend;

		public Sender(final ClientDavInterface connection, final ResultData data) {
			_connection = connection;
			_data = data;
			_lock = new ReentrantLock();
			_send = _lock.newCondition();
		}

		@Override
		public void dataRequest(SystemObject object, DataDescription dataDescription, byte state) {
			try {
				if(state == START_SENDING) {
					_connection.sendData(_data);
					_connection.unsubscribeSender(this, object, dataDescription);
					_lock.lock();
					try {
						_hasSend = true;
						_send.signalAll();
					}
					finally {
						_lock.unlock();
					}
				}
				else {
					_debug.warning("Negative Sendesteuerung", state);
				}
			}
			catch(SendSubscriptionNotConfirmed ignored) {
				return;
			}
		}

		@Override
		public boolean isRequestSupported(SystemObject object, DataDescription dataDescription) {
			return true;
		}

		public void await() throws InterruptedException {
			_lock.lock();
			try {
				while(!_hasSend) {
					_send.await();
				}
			}
			finally {
				_lock.unlock();
			}
		}
	}
}
