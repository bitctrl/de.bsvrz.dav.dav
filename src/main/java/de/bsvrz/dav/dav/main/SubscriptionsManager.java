/*
 * Copyright 2013 by Kappich Systemberatung Aachen
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
import de.bsvrz.dav.daf.communication.lowLevel.telegrams.TransmitterSubscriptionType;
import de.bsvrz.dav.dav.subscriptions.TransmitterCommunicationInterface;
import de.bsvrz.dav.dav.subscriptions.SubscriptionInfo;
import de.bsvrz.dav.dav.util.accessControl.UserAction;
import de.bsvrz.dav.dav.util.accessControl.UserRightsChangeHandler;

import java.util.List;
import java.util.Set;

/**
 * Klasse f�r die Verwaltung der Anmeldungen
 *
 * @author Kappich Systemberatung
 * @version $Revision: 11556 $
 */
public interface SubscriptionsManager extends UserRightsChangeHandler {

	/**
	 * Gibt zu einer baseSubscriptionInfo die zugeh�rige Anmeldungsinfo zur�ck oder erstellt diese falls sie nicht existiert. Nachdem die
	 * Benutzung des Objekts beendet ist, muss {@link de.bsvrz.dav.dav.subscriptions.SubscriptionInfo#close()} aufgerufen werden, damit
	 * eventuelle Aufr�umarbeiten erledigt werden k�nnen.
	 *
	 * @param baseSubscriptionInfo baseSubscriptionInfo
	 * @return Anmeldungsklasse
	 */
	SubscriptionInfo openSubscriptionInfo(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Gibt zu einer baseSubscriptionInfo die zugeh�rige Anmeldungsinfo zur�ck. Nachdem die Benutzung des Objekts beendet ist, muss (sofern
	 * R�ckgabewert != null) {@link de.bsvrz.dav.dav.subscriptions.SubscriptionInfo#close()} aufgerufen werden, damit eventuelle
	 * Aufr�umarbeiten erledigt werden k�nnen.
	 *
	 * @param baseSubscriptionInfo baseSubscriptionInfo
	 * @return Anmeldungsklasseoder null falls nicht existent
	 */
	SubscriptionInfo openExistingSubscriptionInfo(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Gibt zu einer baseSubscriptionInfo die zugeh�rige Anmeldungsinfo zur�ck. Im Unterschied zu openExistingSubscriptionInfo wird das Objekt
	 * nicht f�r Anmeldungen ge�ffnet, es d�rfen daher keine An-/Abmeldungen durchgef�hrt werden.
	 *
	 * @param baseSubscriptionInfo baseSubscriptionInfo
	 * @return Anmeldungsklasse oder null falls zu dieser baseSubscriptionInfo keine Anmeldungsinfo vorliegt
	 */
	SubscriptionInfo getSubscriptionInfo(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * F�hrt eine Rechtepr�fung durch
	 *
	 * @param userId Benutzer-ID
	 * @param info   Anmeldeinfo
	 * @param action Aktion
	 * @return true wenn die Aktion erlaubt ist, sonst false
	 */
	boolean isActionAllowed(long userId, BaseSubscriptionInfo info, UserAction action);

	/**
	 * Pr�ft von allen Anmeldungen die den Benutzer betreffen die Rechte erneut
	 *
	 * @param userId Id des Benutzers
	 */
	@Override
	void handleUserRightsChanged(long userId);

	/**
	 * Wird aufgerufen, wenn dieser Datenverteiler f�r eine Anmeldung Zentraldatenverteiler geworden ist, z.B. um die Anmeldelisten zu
	 * aktualisieren
	 *
	 * @param baseSubscriptionInfo
	 */
	void notifyIsNewCentralDistributor(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Wird aufgerufen, wenn dieser Datenverteiler f�r eine Anmeldung nicht mehr Zentraldatenverteiler ist, z.B. um die Anmeldelisten zu
	 * aktualisieren
	 *
	 * @param baseSubscriptionInfo
	 */
	void notifyWasCentralDistributor(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Gibt zu einer Anmeldungen die potentiellen Zentraldatenverteiler zur�ck
	 *
	 * @param baseSubscriptionInfo Anmeldung
	 * @return Zentraldatenverteiler-IDs
	 */
	List<Long> getPotentialCentralDistributors(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Gibt die beste Verbindung zum angegebenen Datenverteiler zur�ck
	 *
	 * @return die beste Verbindung zum angegebenen Datenverteiler
	 */
	TransmitterCommunicationInterface getBestConnectionToRemoteDav(long remoteDav);

	/**
	 * Entfernt eine Anmeldeinformation. Es d�rfen beim Aufruf dieser Methode keine Anmeldungen mehr bestehen.
	 *
	 * @param subscriptionInfo Anmeldeinformation
	 */
	void removeSubscriptionInfo(SubscriptionInfo subscriptionInfo);

	/**
	 * F�hrt Anmeldungen bei potentiellen Zentraldatenverteilern auf eine Senke durch
	 *
	 * @param subscriptionInfo  Anmeldeinformation
	 * @param distributorsToUse Liste mit zu ber�cksichtigenden potentiellen Zentraldatenverteilern
	 */
	void connectToRemoteDrains(SubscriptionInfo subscriptionInfo, final Set<Long> distributorsToUse);

	/**
	 * F�hrt Anmeldungen bei potentiellen Zentraldatenverteilern auf eine Quelle durch
	 *
	 * @param subscriptionInfo  Anmeldeinformation
	 * @param distributorsToUse Liste mit zu ber�cksichtigenden potentiellen Zentraldatenverteilern
	 */
	void connectToRemoteSources(SubscriptionInfo subscriptionInfo, final Set<Long> distributorsToUse);

	/**
	 * Wandlt eine Anmeldung in einen darstellbaren Text um
	 *
	 * @param baseSubscriptionInfo Anmeldung
	 * @return Text
	 */
	String subscriptionToString(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Wandelt ein Objekt in darstellbaren Text um
	 *
	 * @param objectId Objekt
	 * @return Text
	 */
	String objectToString(long objectId);

	/**
	 * Gibt die eigene Dav-ID zur�ck
	 *
	 * @return die eigene Dav-ID
	 */
	long getThisTransmitterId();

	/**
	 * behandelt eien Anmeldungsquittung von einem anderen Datenverteiler
	 *
	 * @param communication               Verbindung zum anderen Dav
	 * @param transmitterSubscriptionType Art der Anmeldung (Sender/Empf�nger)
	 * @param baseSubscriptionInfo        Anmeldeinformation
	 * @param connectionState             R�ckmeldung des anderen Datenverteilers (Zust�ndig, nicht zust�ndig, etc.)
	 * @param mainTransmitterId           Zentraldatenverteiler-ID sofern verf�gbar
	 */
	void handleTransmitterSubscriptionReceipt(
			TransmitterCommunicationInterface communication,
			TransmitterSubscriptionType transmitterSubscriptionType,
			BaseSubscriptionInfo baseSubscriptionInfo,
			ConnectionState connectionState,
			long mainTransmitterId);

	/**
	 * Wird aufgerufen, wenn es zu einer Dav-id eine bessere Route gibt, sorgt f�r entsprechende Anmeldeumleitungen
	 *
	 * @param transmitterId Dav-Id
	 * @param oldConnection Alte Verbindung
	 * @param newConnection Neue bessere Verbindung
	 */
	void updateDestinationRoute(long transmitterId, TransmitterCommunicationInterface oldConnection, TransmitterCommunicationInterface newConnection);

	/**
	 * Gibt den n�chsten Datenindex f�r die angegebene Anmeldung zur�ck und z�hlt den Index entsprechend hoch
	 *
	 * @param baseSubscriptionInfo Anmeldung
	 * @return Datenindex
	 */
	long getNextDataIndex(BaseSubscriptionInfo baseSubscriptionInfo);

	/**
	 * Gibt den aktuellen (zuletzt generierten) Datenindex f�r die angegebene Anmeldung zur�ck
	 *
	 * @param baseSubscriptionInfo Anmeldung
	 * @return Datenindex
	 */
	long getCurrentDataIndex(BaseSubscriptionInfo baseSubscriptionInfo);

}
