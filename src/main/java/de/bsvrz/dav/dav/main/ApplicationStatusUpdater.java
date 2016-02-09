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

package de.bsvrz.dav.dav.main;

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.BaseSubscriptionInfo;
import de.bsvrz.dav.daf.main.*;
import de.bsvrz.dav.daf.main.config.*;
import de.bsvrz.dav.dav.communication.appProtocol.T_A_HighLevelCommunication;
import de.bsvrz.dav.dav.communication.appProtocol.T_A_HighLevelCommunicationInterface;
import de.bsvrz.dav.dav.subscriptions.ApplicationCommunicationInterface;
import de.bsvrz.dav.dav.subscriptions.LocalReceivingSubscription;
import de.bsvrz.dav.dav.subscriptions.LocalSendingSubscription;
import de.bsvrz.dav.dav.subscriptions.Subscription;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.*;

/**
 * Dieser Thread verschickt den Status(angemeldete Applikationen, Telegrammlaufzeiten, durch Applikationen angemeldete Datenidentifikationen) aller angemeldeten
 * Applikationen.
 */
public final class ApplicationStatusUpdater extends Thread {

	/**
	 * ATG, die ben�tigt wird um einen Datensatz zu verschicken, der alle angemeldeten Applikationen enth�lt. Ist diese ATG nicht vorhanden, so wird der
	 * Datenverteiler gestartet ohne diese Funktionalit�t zur Verf�gung zu stellen.
	 */
	private static final String _pidAngemeldeteApplikationen = "atg.angemeldeteApplikationen";

	/**
	 * ATG, die ben�tigt wird um einen Datensatz zu verschicken, der die angemeldeten Daten aller angemeldeten Applikationen enth�lt. Ist diese ATG nicht
	 * vorhanden, so wird der Datenverteiler gestartet ohne diese Funktionalit�t zur Verf�gung zu stellen.
	 */
	private static final String _pidAngemeldeteDatenidentifikationen = "atg.angemeldeteDatenidentifikationen";

	/**
	 * ATG, die ben�tigt wird um einen Datensatz zu verschicken, der die Telegrammlaufzeiten aller angemeldeten Applikationen enth�lt. Ist diese ATG nicht
	 * vorhanden, so wird der Datenverteiler gestartet ohne diese Funktionalit�t zur Verf�gung zu stellen.
	 */
	private static final String _pidTelegrammLaufzeiten = "atg.telegrammLaufzeiten";


	private static final Debug _debug = Debug.getLogger();

	/** Enth�lt alle Applikationen, die an/ab gemeldet werden sollen. Die Elemente werden nach Fifo bearbeitet. */
	private final List<ApplicationConnection> _application = Collections.synchronizedList(new ArrayList<ApplicationConnection>());

	private final SystemObject _davObject;

	/** Zum verschicken aller angemeldeten Applikationen */
	private final SourceApplicationUpdater _applicationUpdater = new SourceApplicationUpdater(this);

	/** DataDescription, mit der eine Quelle zum verschicken aller angemeldeten Applikationen angemeldet wird. */
	private final DataDescription _applicationUpdaterDataDescription;

	/** Zum verschicken aller Telegrammlaufzeiten der angemeldeten Applikationen */
	private final SourceApplicationUpdater _applicationRuntime = new SourceApplicationUpdater(this);

	/** DataDescription, mit der eine Quelle zum verschicken der Telegrammlaufzeiten der angemeldeten Applikationen. */
	private final DataDescription _applicationRuntimeDataDescription;

	/** Die DataDescription, die zum Versandt von Anmeldungen einer Applikation benutzt wird. */
	private final DataDescription _applicationDataDescriptionDD;

	/** Verschickt zyklisch alle angemeldeten Applikationen und deren Telegrammlaufzeiten. */
	private final Timer _timer = new Timer("Status der Applikation zyklisch verschicken", true);

	/**
	 * Wird f�r eine Applikation eine neue Datenidentifikation angemeldet, so wird dies �ber einen TimerTask publiziert. Der TimerTask, der diese Aufgabe
	 * �bernimmt, wird hier gespeichert. Der TimerTask kann jederzeit unterbrochen werden und durch einen neuen TimerTask ersetzt werden. Auch wenn der TimerTask
	 * bereits ausgef�hrt wurde befindet er sich weiterhin in dieser Map und kann durch einen neuen TimerTask ersetzt werden.
	 * <p/>
	 * Als Schl�ssel dient die HighLevelCommu, dies entspricht der Applikation, deren Anmeldungen verschickt werden sollen. Als Value wird ein TimerTask
	 * zur�ckgegeben, der alle angemeldeten Datenidentifikationen der Applikation verschickt, sobald der Thread ausgef�hrt wird. Der Thread kann bereits ausgef�hrt
	 * worden sein oder aber noch ausgef�hrt werden (oder befindet sich in Bearbeitung). Wurde noch kein TimerTask angelegt, so wird <code>null</code>
	 * zur�ckgegeben.
	 */
	private final Map<ApplicationCommunicationInterface, TimerTask> _threadsForDataIdentificationUpdates = new HashMap<ApplicationCommunicationInterface, TimerTask>();

	/**
	 * Key = Applikation, die bereits Daten angemeldet hat; Value = ClientSenderInterface(SourceApplicationUpdater). Damit kann sp�ter die Verbindung wieder
	 * abgemeldet werden und es wird ein mehrfaches anmelden verhindert. Gleichzeit kann das Objekt befragt werden, ob �berhaupt gesendet werden soll.
	 */
	private final Map<T_A_HighLevelCommunication, SourceApplicationUpdater> _subcribedDataIdentifications = new HashMap<T_A_HighLevelCommunication, SourceApplicationUpdater>();

	private final Object _applicationConnections = new Object();

	private final DataModel _dataModel;

	private final HighLevelConnectionsManagerInterface _connectionsManager;

	private final ClientDavInterface _connection;

	public ApplicationStatusUpdater(final HighLevelConnectionsManagerInterface connectionsManager, final ClientDavInterface connection) {
		_connectionsManager = connectionsManager;
		_connection = connection;
		_dataModel = _connection.getDataModel();
		try {
			this.setDaemon(true);

			setName("Applikationsstatus-Updater");

			// Folgender Code w�re falsch, da getLocalDav bei Remote-Dav-Verbindungen (also Datenverteilern ohne eigener Konfiguration)
			// Den Konfigurations-Dav ausspucken w�rde und nicht den hier verwendeten
//			_davObject = _connection.getLocalDav();

			_davObject = connectionsManager.getDavObject();

			// Als Quelle f�r alle angemeldeten Applikationen anmelden

			final AttributeGroup applicationUpdaterATG = _dataModel.getAttributeGroup(_pidAngemeldeteApplikationen);
			final Aspect applicationUpdaterAspect = _dataModel.getAspect("asp.standard");
			_applicationUpdaterDataDescription = new DataDescription(applicationUpdaterATG, applicationUpdaterAspect);
			_connection.subscribeSender(_applicationUpdater, _davObject, _applicationUpdaterDataDescription, SenderRole.source());

			// Als Quelle f�r Telegrammlaufzeiten zu allen angemeldeten Applikationen anmelden

			final AttributeGroup applicationTelegramRuntimeATG = _dataModel.getAttributeGroup(_pidTelegrammLaufzeiten);
			final Aspect applicationTelegramRuntimeAspect = _dataModel.getAspect("asp.messwerte");
			_applicationRuntimeDataDescription = new DataDescription(applicationTelegramRuntimeATG, applicationTelegramRuntimeAspect);
			_connection.subscribeSender(_applicationRuntime, _davObject, _applicationRuntimeDataDescription, SenderRole.source());

			// Datenidentifikation zum verschicken von "Datenidentifikationen, die zu einer Applikation geh�ren" anlegen
			_applicationDataDescriptionDD = new DataDescription(
					_dataModel.getAttributeGroup(_pidAngemeldeteDatenidentifikationen), _dataModel.getAspect("asp.standard")
			);
			start();
		}
		catch(OneSubscriptionPerSendData oneSubscriptionPerSendData) {
			final String errorMessage = "Der Thread zur Publizierung des Status aller angemeldeten Applikationen konnte nicht gestartet werden.";
			_debug.error(
					errorMessage, oneSubscriptionPerSendData
			);
			throw new IllegalStateException(errorMessage);
		}
	}


	/**
	 * Pr�ft, ob das �bergebene Objekt gleich dem Objekt ist, das die Applikation des DaV darstellt, die sich selbst beim DaV anmeldet oder aber die
	 * Konfiguration.
	 * <p/>
	 * Wurde bisher noch kein DaV-Objekt gesucht/gefunden und das �bergebene Objekt ist das DaV-Objekt, so wird das Objekt einen internen Variablen zugewiesen und
	 * <code>true</code> zur�ck gegeben.
	 *
	 * @param unknownObject Objekt, das vielleicht das DaV-Objekt darstellen k�nnte.
	 *
	 * @return true = Es handelt sich um das DaV-Objekt oder das Konfigurationsobjekt -> Das Objekt muss ausgeblendet werden; false = sonst
	 */
	private boolean isNotSpecialTreatedApplicationObject(final ServerHighLevelCommunication unknownObject) {

		// 1) Die Konfiguration besitzt weder ein Systemobjekt noch einen Benutzer
		// 2) Das Applikationsobjekt des Datenverteilers ist vom Typ "typ.datenverteiler"

		final long applicationId = unknownObject.getId();
		if(applicationId == 0 || applicationId == -1) return false;
		final SystemObject unknownSystemobject = _dataModel.getObject(applicationId);
		final SystemObject unknownUser = _dataModel.getObject(unknownObject.getRemoteUserId());

		if(unknownSystemobject == null) {
			if(unknownUser == null) {
				return false;
			}
			else {
				_debug.error("Es gibt kein Systemobjekt, aber einen Benutzer: " + unknownUser);
				return false;
			}
		}
		else {
			// Das Systemobjekt ist vorhanden, hat es den richtigen Typ ?
			return unknownSystemobject instanceof ClientApplication;
		}
	}

	/** Es wird ein Datensatz verschickt, der alle Applikationen enth�lt, die gerade am DaV angemeldet sind. */
	private void sendApplicationUpdate() {
		ArrayList<T_A_HighLevelCommunication> applicationConnectionsCopy = null;
		if(_applicationUpdater.sendData()) {
			// Es gibt einen Empf�nger f�r die Daten
			applicationConnectionsCopy = new ArrayList<T_A_HighLevelCommunication>(_connectionsManager.getAllApplicationConnections());
		}
		if(applicationConnectionsCopy != null) {
			// Es gibt einen Empf�nger f�r die Daten
			final Data data = _connection.createData(_applicationUpdaterDataDescription.getAttributeGroup());

			// Alle Applikationen eintragen, die derzeit angemeldet sind.
			final Data.Array subscribedApplications = data.getItem("angemeldeteApplikation").asArray();
			// Diese gr��e pa�t vielleicht nicht. Das Applikationsobjekt f�r die Konfiguration und das Applikationsobjekt f�r den DaV muss noch entfernt werden.
			subscribedApplications.setLength(applicationConnectionsCopy.size());

			// Index, an dem ein neues Element in das Array eingef�gt werden muss
			int dataIndex = 0;

			for(final T_A_HighLevelCommunication applicationConnection : applicationConnectionsCopy) {
				if(isNotSpecialTreatedApplicationObject(applicationConnection)) {
					// atl.angemeldeteApplikation
					final Data listEntry = subscribedApplications.getItem(dataIndex);

					final SystemObject subscribedApplicationSystemObject = _dataModel.getObject(applicationConnection.getId());
					listEntry.getItem("applikation").asReferenceValue().setSystemObject(subscribedApplicationSystemObject);

					final SystemObject subscribedUserSystemObject = _dataModel.getObject(applicationConnection.getRemoteUserId());
					listEntry.getItem("benutzer").asReferenceValue().setSystemObject(subscribedUserSystemObject);

					listEntry.getItem("seit").asTimeValue().setMillis(applicationConnection.getConnectionCreatedTime());

					listEntry.getItem("sendepufferzustand").asTextValue().setText(applicationConnection.getSendBufferState());

					dataIndex++;
				}
				else {
					// Dieses Applikationsobjekt darf nicht mit verschickt werden. Also das urspr�ngliche Array verkleinern.
					subscribedApplications.setLength(subscribedApplications.getLength() - 1);
				}
			} // for, �ber alle angemeldeten Applikationen

			sendDataAsSource(data, _applicationUpdaterDataDescription);
		}
	}

	/** Verschickt ein Telegramm, das f�r alle angemeldeten Applikationen die Telegrammlaufzeit vom DaV zur Applikation enth�lt. */
	private void sendApplicationTelegramRuntimeUpdate() {
		if(_applicationRuntime.sendData()) {
			// Es gibt einen Empf�nger f�r die Daten

			final ArrayList<T_A_HighLevelCommunication> applicationConnections;
			applicationConnections = new ArrayList<T_A_HighLevelCommunication>(_connectionsManager.getAllApplicationConnections());

			final Data data = _connection.createData(_applicationRuntimeDataDescription.getAttributeGroup());
			final Data.Array subscribedApplications = data.getItem("telegrammLaufzeit").asArray();
			subscribedApplications.setLength(applicationConnections.size());

			// Index, an dem ein neues Element in das Array eingef�gt werden muss
			int dataIndex = 0;

			for(final T_A_HighLevelCommunication applicationConnection : applicationConnections) {
				if(isNotSpecialTreatedApplicationObject(applicationConnection)) {
					// atl.telegrammLaufzeit
					final Data listEntry = subscribedApplications.getItem(dataIndex);

					final SystemObject subscribedApplicationSystemObject = _dataModel.getObject(applicationConnection.getId());
					listEntry.getItem("applikation").asReferenceValue().setSystemObject(subscribedApplicationSystemObject);

					final int maxWaitingTime = 30000;
					long roundTripTime;
					try {
						roundTripTime = applicationConnection.getTelegramTime(maxWaitingTime);
						if(roundTripTime < 0) roundTripTime = maxWaitingTime;
					}
					catch(CommunicationError communicationError) {
						roundTripTime = maxWaitingTime;
						_debug.warning(
								"Fehler bei der Ermittlung der Telegrammlaufzeit: betroffene Applikation: "
								+ _dataModel.getObject(applicationConnection.getId())
						);
					}
					listEntry.getItem("laufzeit").asUnscaledValue().set(roundTripTime);
					dataIndex++;
				}
				else {
					// Dieses Applikationsobjekt darf nicht mit verschickt werden. Also das urspr�ngliche Array verkleinern.
					subscribedApplications.setLength(subscribedApplications.getLength() - 1);
				}
			} // for, �ber alle angemeldeten Applikationen

			sendDataAsSource(data, _applicationRuntimeDataDescription);
		}
	}

	private void sendDataAsSource(final Data data, final DataDescription applicationRuntimeDataDescription) {
		final ResultData resultData = new ResultData(_davObject, applicationRuntimeDataDescription, System.currentTimeMillis(), data);
		try {
			_connection.sendData(resultData);
		}
		catch(SendSubscriptionNotConfirmed sendSubscriptionNotConfirmed) {
			final String message = "Telegramm konnte wegen fehlender Sendesteuerung nicht versendet werden."
			                       + " Dies kann hier wegen der Anmeldung als Quelle nicht vorkommen";
			_debug.error(message, sendSubscriptionNotConfirmed);
			throw new RuntimeException(message, sendSubscriptionNotConfirmed);
		}
	}

	/**
	 * Verschickt einen Datensatz mit der ATG "atg.angemeldeteDatenidentifikationen".
	 *
	 * @param application Applikation, deren angemeldete Datenidentifiaktionen propagiert werden sollen.
	 */
	private void sendDataDescriptionUpdate(final ApplicationCommunicationInterface application) {
		final SourceApplicationUpdater updater;
		synchronized(_subcribedDataIdentifications) {
			// Dieses Objekt hat die Sendesteuerung
			updater = _subcribedDataIdentifications.get(application);
		}
		if(updater != null) {
			if(updater.sendData()) {
				// Es gibt Empf�nger f�r die Daten

				// Dieses Objekt besitzt alle Anmeldungen (Sender/Empf�nger) und die entsprechenden Rollen(Quelle,Senke,Sender,Empf�nger)
				final Collection<? extends Subscription> subscriptionsFromRemoteStorage = _connectionsManager.getSubscriptionsManager().getAllSubscriptions(
						application
				);

				// T_T sollte nicht gebraucht werden, da nur angemeldete Applikationen auf dem neusten Stand gehalten werden sollen.

				final Data data = _connection.createData(_applicationDataDescriptionDD.getAttributeGroup());
				final Data.Array subscribedApplications = data.getItem("angemeldeteDatenidentifikation").asArray();

				// Alle angemldeten Datenidentifikation der Applikation anfordern

				subscribedApplications.setLength(subscriptionsFromRemoteStorage.size());

				// Index, an dem ein neues Element in das Array eingef�gt werden muss
				int dataIndex = 0;

				// Alle Anmeldungen der Applikation eintrage, die Daten empfangen.
				for(Subscription subscription : subscriptionsFromRemoteStorage) {
					final Data listEntry = subscribedApplications.getItem(dataIndex);
					dataIndex++;

					String role = "?";
					if(subscription instanceof LocalReceivingSubscription) {
						LocalReceivingSubscription localReceivingSubscription = (LocalReceivingSubscription)subscription;


						if(localReceivingSubscription.isDrain()) {
							role = "Senke";
						}
						else {
							role = "Empf�nger";
						}

					}
					else if(subscription instanceof LocalSendingSubscription) {
						LocalSendingSubscription localSendingSubscription = (LocalSendingSubscription)subscription;

						if(localSendingSubscription.isSource()) {
							role = "Quelle";
						}
						else {
							role = "Sender";
						}

					}
					inscribeDataDescription(listEntry, subscription.getBaseSubscriptionInfo(), role);
					
					if(!listEntry.isDefined()) {
						BaseSubscriptionInfo baseSubscriptionInfo = subscription.getBaseSubscriptionInfo();
						long objectID = baseSubscriptionInfo.getObjectID();
						long usageIdentification = baseSubscriptionInfo.getUsageIdentification();
						short simulationVariant = baseSubscriptionInfo.getSimulationVariant();
						_debug.warning(
								"Fehler bei der Abfrage der angemeldeten Datenidentifikationen der Applikation " + application.toString()
										+ ", Objekt:"
										+ listEntry.getTextValue("objekt").getValueText() + " (" + objectID
										+ "), Attributgruppenverwendung: "
										+ listEntry.getTextValue("attributgruppenverwendung").getValueText()
										+ " (" + usageIdentification + "), Rolle: " + role + ", Simulationsvariante: " + simulationVariant

						);
						dataIndex--;
					}
				}

				// Datensatz ggf. verk�rzen, falls einzelne Eintr�ge aufgrund eines Fehlers nicht eingef�gt werden konnten
				subscribedApplications.setLength(dataIndex);

				// Es sind alle Anmeldungen am Data vermerkt, der Datensatz kann verschickt werden.

				final SystemObject systemObjectApplication = _dataModel.getObject(application.getId());

				final ResultData resultData = new ResultData(
						systemObjectApplication, _applicationDataDescriptionDD, System.currentTimeMillis(), data
				);

				try {
					_connection.sendData(resultData);
				}
				catch(SendSubscriptionNotConfirmed sendSubscriptionNotConfirmed) {
					final String message = "Telegramm konnte wegen fehlender Sendesteuerung nicht versendet werden."
					                       + " Dies kann hier wegen der Anmeldung als Quelle nicht vorkommen";
					_debug.error(message, sendSubscriptionNotConfirmed);
					throw new RuntimeException(message, sendSubscriptionNotConfirmed);
				}
			}
		}
	}

	/**
	 * Schreibt in den �bergebenen Datensatz(atl.angemeldeteDatenidentifikation) alle Daten ben�tigten Daten.
	 *
	 * @param data                 Datensatz (atl.angemeldeteDatenidentifikation)
	 * @param baseSubscriptionInfo Enth�lt das SystemObject(Id), die verwendete ATGV (Id) und die Simulationsvariante
	 * @param role                 Verwendete Rolle, siehe auch att.rolleAnmeldung. Zul�ssige Werte: Quelle, Sender, Senke, Empf�nger
	 */
	private void inscribeDataDescription(
			final Data data, final BaseSubscriptionInfo baseSubscriptionInfo, final String role) {
		final SystemObject subscribedObject = _dataModel.getObject(baseSubscriptionInfo.getObjectID());
		data.getItem("objekt").asReferenceValue().setSystemObject(subscribedObject);

		final AttributeGroupUsage usage = _dataModel.getAttributeGroupUsage(baseSubscriptionInfo.getUsageIdentification());
		data.getItem("attributgruppenverwendung").asReferenceValue().setSystemObject(usage);

		data.getItem("simulationsvariante").asUnscaledValue().set(baseSubscriptionInfo.getSimulationVariant());

		data.getItem("rolle").asTextValue().setText(role);
	}

	/**
	 * Erstellt einen TimerTask, der alls 60 Sekunden alle angemeldeten Applikationen und deren Telegrammlaufzeiten verschickt (wenn ein Empf�nger angemeldet
	 * ist).
	 */
	private void createPeriodicUpdateTask() {
		final TimerTask task = new TimerTask() {
			@Override
			public void run() {
				try {
					sendApplicationUpdate();
				}
				catch(Exception e) {
					_debug.warning("Fehler beim Versand der Statusinformation mit angemeldeten Applikationen", e);
					return;
				}
				try {
					sendApplicationTelegramRuntimeUpdate();
				}
				catch(Exception e) {
					_debug.warning("Fehler beim Versand der Statusinformation mit den Telegrammlaufzeiten der Applikationen", e);
				}
			}
		};
		// In 60 Sekunden starten, alle 60 Sekunden wiederholen
		_timer.schedule(task, 60000, 60000);
	}

	/**
	 * Meldet f�r eine neue Applikation eine Quelle an, die Datens�tze vom mit der ATG "atg.angemeldeteDatenidentifikationen" verschickt.
	 * <p/>
	 * Gleichzeitig wird das Objekt in alle ben�tigten Datenstrukturen eingetragen.
	 * <p/>
	 * Hat bereits eine Anmeldung stattgefunden, wird nichts gemacht (zum Beispiel, wenn weiter DataDescription f�r eine Applikation angemeldet werden).
	 *
	 * @param newApplication enth�lt alle Daten zum anmelden.
	 */
	private void subscribeDataDescriptionSource(final T_A_HighLevelCommunication newApplication) {
		synchronized(_subcribedDataIdentifications) {
			if(!_subcribedDataIdentifications.containsKey(newApplication)) {
				// Die besonderen Applikationen(Konfiguration, Objekt, das den DaV darstellt) verschicken ihren Status nicht
				if(isNotSpecialTreatedApplicationObject(newApplication)) {
					// Bisher hat keine Anmeldung stattgefunden.
					final AttributeGroup attributeGroup = _dataModel.getAttributeGroup(_pidAngemeldeteDatenidentifikationen);
					final Aspect aspect = _dataModel.getAspect("asp.standard");

					final DataDescription dataDescription = new DataDescription(attributeGroup, aspect);

					final SystemObject applicationObject = _dataModel.getObject(newApplication.getId());

					final SourceApplicationUpdater sourceApplicationUpdater = new SourceApplicationUpdater(this);

					try {
						_connection.subscribeSender(sourceApplicationUpdater, applicationObject, dataDescription, SenderRole.source());
						_subcribedDataIdentifications.put(newApplication, sourceApplicationUpdater);
					}
					catch(OneSubscriptionPerSendData oneSubscriptionPerSendData) {
						_debug.error(
								"F�r eine Applikation kann keine Quelle angemeldet werden, die alle angemeldeten Datenidentifikationen der Applikation publiziert.",
								oneSubscriptionPerSendData
						);
					}
				}
			}
		}
	}

	/**
	 * Meldet die Datens�tze der ATG "atg.angemeldeteDatenidentifikationen" wieder ab und entfernt die Objekte aus allen Datenstrukturen. Wurden die Daten bereits
	 * abgemeldet wird nichts gemacht.
	 * <p/>
	 * Eventuell angemeldete TimerTasks werden unterbrochen und entfernt.
	 *
	 * @param removedApplication Objekt, deren Quellenanmeldung zur�ckgenommen werden soll.
	 */
	private void unsubscribeDataDescriptionSource(final T_A_HighLevelCommunication removedApplication) {
		final SourceApplicationUpdater updater;
		synchronized(_subcribedDataIdentifications) {
			updater = _subcribedDataIdentifications.remove(removedApplication);
		}
		if(updater != null) {
			// Das Objekt war noch vorhanden, also k�nnen die Daten abgemeldet werden.
			final AttributeGroup attributeGroup = _dataModel.getAttributeGroup(_pidAngemeldeteDatenidentifikationen);
			final Aspect aspect = _dataModel.getAspect("asp.standard");

			final DataDescription dataDescription = new DataDescription(attributeGroup, aspect);

			final SystemObject applicationObject = _dataModel.getObject(removedApplication.getId());

			_connection.unsubscribeSender(updater, applicationObject, dataDescription);
		}

		// Falls es noch einen TimerTask gibt, der die angemeldeten Datenidentifikationen verschicken will, wird dieser beendet.
		// Dies ist n�tig, weil die Applikation sich vom DaV abgemeldet hat.
		synchronized(_threadsForDataIdentificationUpdates) {
			final TimerTask taskForDataIdentificationUpdate = _threadsForDataIdentificationUpdates.remove(removedApplication);
			if(taskForDataIdentificationUpdate != null) {
				taskForDataIdentificationUpdate.cancel();
			}
		}
	}

	@Override
	public void run() {

		// Alle angemeldeten Applikationen und die Telegrammlaufzeit werden zyklisch alle 60 Sekunden verschickt.
		createPeriodicUpdateTask();

		while(!isInterrupted()) {
			final ApplicationConnection newApplicationDetected;
			synchronized(_applicationConnections) {
				// Solange warten, bis es eine Applikation gibt, die bearbeitet werden muss
				while(_application.size() == 0) {
					// Wenn <code>_applicationConnections</code> sich �ndert, wird in _application die Applikation abgelegt, die ge�ndert wurde.
					try {
						_applicationConnections.wait();
					}
					catch(InterruptedException e) {
						_debug.error("Der Thread wurde mit Interrupt beendet", e);
						// Da der Thread beendet wurde, wird die while-Schleife verlassen ohne Daten zu verschicken (vielleicht gibt es zu diesem
						// Zeitpunkt auch nichts, was verschickt werden soll).
						return;
					}
				} // while, nichts zu tun
				//Fifo
				newApplicationDetected = _application.remove(0);
			} // synch neue Applikationen

			final T_A_HighLevelCommunication newApplicationConnection = newApplicationDetected.getApplicationConnection();

			if(newApplicationDetected.isAdded()) {
				// 1) Es wurde eine Applikation hinzugef�gt -> Quelle f�r die angemeldetenDatenidentifikationen der Applikation anmelden.
				subscribeDataDescriptionSource(newApplicationConnection);
			}
			else {
				// 1) Quelle f�r angemeldetenDatenidentifikationen dieser Applikation abmelden und den TimerTask beenden(falls vorhanden)
				unsubscribeDataDescriptionSource(newApplicationConnection);
			}

			// Alle benachrichtigen, dass eine Applikation hinzugef�gt oder entfernt wurde
			sendApplicationUpdate();
		}
	}

	/**
	 * F�gt eine neue Applikation den Datenstrukturen hinzu und der Thread, der Aktualisierungsdatens�tze verschickt, wird aufgeweckt.
	 *
	 * @param applicationConnection Neue Applikation
	 */
	public void applicationAdded(final T_A_HighLevelCommunication applicationConnection) {
		synchronized(_applicationConnections) {
			_application.add(new ApplicationConnection(applicationConnection, true));
			// Es wurde eine Applikation hinzugef�gt, der Thread muss aufwachen und ein Telegramm verschicken
			_applicationConnections.notifyAll();
		}
	}

	/**
	 * Speichert die entfernte Applikation und weckt den wartende Thread auf, der daraufhin eine Datensatz mit den aktuell angemeldeten Applikationen verschickt.
	 *
	 * @param applicationConnection Applikation, die entfernt wurde
	 */
	public void applicationRemoved(final T_A_HighLevelCommunication applicationConnection) {
		synchronized(_applicationConnections) {
			_application.add(new ApplicationConnection(applicationConnection, false));
			// Es wurde eine Applikation entfernt, der Thread muss aufwachen und ein Telegramm verschicken
			_applicationConnections.notifyAll();
		}
	}

	/**
	 * Diese Methode wird aufgerufen, wenn eine Applikation eine neue Datenidentifikation anmeldet.
	 * <p/>
	 * F�r diese Applikation wird ein Datensatz verschickt, der alle angemeldeten Datenidentifikationen enth�lt.
	 *
	 * @param application Applikation, die neue Daten anmeldet.
	 */
	public void applicationSubscribedNewConnection(final ApplicationCommunicationInterface application) {
		publishConnectionChanged(application);
	}

	/**
	 * Diese Methode wird aufgerufen, wenn eine Applikation eine  Datenidentifikation abmeldet.
	 * <p/>
	 * F�r diese Applikation wird ein Datensatz verschickt, der alle angemeldeten Datenidentifikationen enth�lt.
	 *
	 * @param application Applikation, die eine Datenidentifikation abmeldet.
	 */
	public void applicationUnsubscribeConnection(final ApplicationCommunicationInterface application) {
		publishConnectionChanged(application);
	}

	/**
	 * Wird aufgerufen, wenn eine Applikation einen Datenidentifikation an/abmeldet. Es wird ein TimerTask angelegt, der nach einer gewissen Zeit alle angemeldeten
	 * Datenidentifikationen der �bergebene Applikation publiziert. Gibt es bereits einen TimerTask, der diese Aufgabe �bernehmen m�chte, so wird dieser gestoppt
	 * und durch einen neuen TimerTask ersetzt, der wieder eine gewisse Zeit wartet.
	 * <p/>
	 * Dadruch wird verhindert, dass sehr viele Anmeldungen eine Flut von Update-Telegrammen ausl�st. Jede neue Anmeldung verz�gert das Update-Telegramm, erst wenn
	 * alle Anmeldungen durchgef�hrt wurden, wird das Telegramm verschickt.
	 *
	 * @param application Applikation, die neue Daten an oder abmeldet.
	 */
	private void publishConnectionChanged(final ApplicationCommunicationInterface application) {
		synchronized(_threadsForDataIdentificationUpdates) {
			final TimerTask taskForThisApplication = _threadsForDataIdentificationUpdates.get(application);

			if(taskForThisApplication != null) {
				// Es gibt bereits einen Task, dieser kann bereits abgelaufen sein oder sich noch in Bearbeitung befinden.
				// (Auch wenn der Task bereits ausgef�hrt wurde, schadet dieser Aufruf nicht)
				taskForThisApplication.cancel();
			}

			// Es muss ein neuer Task angelegt werden
			final TimerTask task = new TimerTask() {
				@Override
				public void run() {
					try {
						sendDataDescriptionUpdate(application);
					}
					catch(Exception e) {
						_debug.warning("Probleme beim Versenden von Zustandsinformation �ber angemeldete Datenidentifikationen", e);
					}
				}
			};
			_threadsForDataIdentificationUpdates.put(application, task);
			// In 5 Sekunden den Task starten, findet eine weitere �nderung statt, wird dieser Task mit cancel gestoppt und ein neuer angelegt
//			_timer.schedule(task, 5000);
			task.run();
		}
	}

	private static final class ApplicationConnection {

		final boolean _added;

		final T_A_HighLevelCommunication _applicationConnection;

		/**
		 * @param applicationConnection Verbindung der Applikation
		 * @param added                 <code>true</code>, wenn die Verbindung zum Datenverteiler hinzuge�fgt wurde. <code>false</code>, sonst.
		 */
		public ApplicationConnection(final T_A_HighLevelCommunication applicationConnection, final boolean added) {
			_applicationConnection = applicationConnection;
			_added = added;
		}


		public boolean isAdded() {
			return _added;
		}

		public T_A_HighLevelCommunication getApplicationConnection() {
			return _applicationConnection;
		}

		@Override
		public String toString() {
			return "ApplicationConnection{" +
			       "_added=" + _added +
			       ", _applicationConnection=" + _applicationConnection +
			       '}';
		}
	}

	private final class SourceApplicationUpdater implements ClientSenderInterface {

		private byte _state = ClientSenderInterface.STOP_SENDING;

		private final ApplicationStatusUpdater _sender;


		public SourceApplicationUpdater(final ApplicationStatusUpdater sender) {
			_sender = sender;
		}

		@Override
		public void dataRequest(final SystemObject object, final DataDescription dataDescription, final byte state) {
			synchronized(this) {

				if(_state != ClientSenderInterface.START_SENDING && state == ClientSenderInterface.START_SENDING) {
					// Der Zustand wird von "nicht senden" auf "senden" ge�ndert -> Daten verschicken
					if(dataDescription.getAttributeGroup().getPid().equals(_pidAngemeldeteApplikationen)) {
						final Thread helper = new Thread("StatusinfoversandApplikationen") {
							@Override
							public void run() {
								try {
									_sender.sendApplicationUpdate();
								}
								catch(Exception e) {
									_debug.warning("Fehler beim Versand der Statusinformation bzgl. der angemeldeten Applikationen", e);
								}
							}
						};
						helper.setDaemon(true);
						helper.start();
					}
					else if(dataDescription.getAttributeGroup().getPid().equals(_pidTelegrammLaufzeiten)) {
						final Thread helper = new Thread("StatusinfoversandTelegrammlaufzeiten") {
							@Override
							public void run() {
								try {
									_sender.sendApplicationTelegramRuntimeUpdate();
								}
								catch(Exception e) {
									_debug.warning("Fehler beim Versand der Statusinformation bzgl. der Telegrammlaufzeiten", e);
								}
							}
						};
						helper.setDaemon(true);
						helper.start();
					}
					else if(dataDescription.getAttributeGroup().getPid().equals(_pidAngemeldeteDatenidentifikationen)) {
						// Die beteiligte T_A_HighLevel finden
						final T_A_HighLevelCommunicationInterface application = _connectionsManager.getApplicationConnectionFromId(object.getId());
						// Abfrage, falls die Application entfernt wurde
						if(application != null && application instanceof T_A_HighLevelCommunication) {
							final Thread helper = new Thread("StatusinfoversandAnmeldungen") {
								@Override
								public void run() {
									try {
										_sender.sendDataDescriptionUpdate((T_A_HighLevelCommunication)application);
									}
									catch(Exception e) {
										_debug.warning("Fehler beim Versand der Statusinformation bzgl. der Anmeldungen einer Applikation", e);
									}
								}
							};
							helper.setDaemon(true);
							helper.start();
						}
					}
				}
				_state = state;
			}
		}

		@Override
		public boolean isRequestSupported(final SystemObject object, final DataDescription dataDescription) {
			return true;
		}

		public boolean sendData() {
			synchronized(this) {
				return _state == ClientSenderInterface.START_SENDING;
			}
		}
	}
}
