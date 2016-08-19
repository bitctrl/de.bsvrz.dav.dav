/*
 * Copyright 2013 by Kappich Systemberatung Aachen
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

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.*;
import de.bsvrz.dav.daf.communication.srpAuthentication.SrpNotSupportedException;
import de.bsvrz.dav.daf.communication.srpAuthentication.SrpVerifierAndUser;
import de.bsvrz.dav.daf.main.authentication.ClientCredentials;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunication;
import de.bsvrz.dav.dav.communication.davProtocol.T_T_HighLevelCommunicationInterface;

/**
 * Interface für die Verwaltung der Dav-Dav_Verbindungen
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public interface HighLevelTransmitterManagerInterface {

	/**
	 * Wird bei einem Verbindungsabbruch aufgerufen
	 *
	 * @param communication
	 */
	void connectionTerminated(T_T_HighLevelCommunication communication);

	/**
	 * Bestimmt den Benutzername der zur Authentifizierung beim angegebenen Datenverteiler benutzt werden soll. Wenn der Benutzername in der
	 * Topologie nicht vorgegeben ist, dann wird der Standardbenutzername des Datenverteilers benutzt.
	 *
	 * @param connectedTransmitterId Objekt-ID des anderen Datenverteilers.
	 * @return Benutzername für die Authentifizierung beim anderen Datenverteiler.
	 */
	String getUserNameForAuthentication(long connectedTransmitterId);

	/**
	 * Bestimmt das Benutzerpasswort das zur Authentifizierung beim angegebenen Datenverteiler benutzt werden soll. Wenn der Benutzername in
	 * der Topologie nicht vorgegeben ist, dann wird das Passwort des Standardbenutzers des Datenverteilers zurückgegeben.
	 *
	 * @param transmitterId Objekt-ID des anderen Datenverteilers.
	 * @return Passwort für die Authentifizierung beim anderen Datenverteiler.
	 */
	ClientCredentials getClientCredentialsForAuthentication(long transmitterId);

	/**
	 * Bestimmt das Benutzerpasswort das zur Authentifizierung beim angegebenen Datenverteiler mit dem angegebenen Benutzernamen benutzt werden soll.
	 *
	 * @param userName  Benutzername
	 * @param transmitterId Objekt-ID des anderen Datenverteilers.
	 * @return Passwort für die Authentifizierung beim anderen Datenverteiler.
	 */
	ClientCredentials getClientCredentialsForAuthentication(String userName, long transmitterId);

	/**
	 * Fragt von der Konfiguration bei einer eingehenden Authentifizierungsanfrage den SRP-Verifier für den angegebenen Benutzer ab
	 * @param userName Benutzername
	 * @return SRP-Überprüfugnscode
	 * @throws SrpNotSupportedException
	 */
	SrpVerifierAndUser fetchSrpVerifierAndAuthentication(String userName) throws SrpNotSupportedException;

	/**
	 * Gibt das Gewicht einer Verbindung zurück
	 *
	 * @param transmitterId Datenverteiler, zu dem das Gewischt ermittelt werden soll
	 * @return
	 */
	short getWeight(long transmitterId);

	/**
	 * Wird aufgerufen, wenn ein Datentelegramm eintrifft
	 *
	 * @param communication           Verbindung über die das Telegram eintrifft
	 * @param transmitterDataTelegram Telegram
	 */
	void handleDataTelegram(T_T_HighLevelCommunication communication, TransmitterDataTelegram transmitterDataTelegram);

	/**
	 * Dieses Telegramm wird an den ListsManager weitergegeben, siehe Dokumentation dort
	 *
	 * @param transmitterListsUpdate telegram
	 */
	void handleListsUpdate(TransmitterListsUpdate transmitterListsUpdate);

	/**
	 * Dieses Telegramm wird an den ListsManager weitergegeben, siehe Dokumentation dort
	 *
	 * @param communication Verbindung über die das Telegram gesendet wurde
	 * @param transmitterListsDeliveryUnsubscription telegram
	 */
	void handleListsDeliveryUnsubscription(
			T_T_HighLevelCommunicationInterface communication, TransmitterListsDeliveryUnsubscription transmitterListsDeliveryUnsubscription);

	/**
	 * Dieses Telegramm wird an den ListsManager weitergegeben, siehe Dokumentation dort
	 *
	 * @param communication  Verbindung über die das Telegram gesendet wurde
	 * @param transmitterListsUnsubscription telegram
	 */
	void handleListsUnsubscription(
			ServerHighLevelCommunication communication, TransmitterListsUnsubscription transmitterListsUnsubscription);

	/**
	 * Dieses Telegramm wird an den ListsManager weitergegeben, siehe Dokumentation dort
	 *
	 * @param communication  Verbindung über die das Telegram gesendet wurde
	 * @param transmitterListsSubscription telegram
	 */
	void handleListsSubscription(
			ServerHighLevelCommunication communication, TransmitterListsSubscription transmitterListsSubscription);

	/**
	 * Eingehende Datenanmeldung
	 *
	 * @param communication Verbindung
	 * @param subscription  Telegram
	 */
	void handleTransmitterSubscription(T_T_HighLevelCommunicationInterface communication, TransmitterDataSubscription subscription);

	/**
	 * Eingehende Datenabmeldung
	 *
	 * @param communication  Verbindung
	 * @param unsubscription Telegram
	 */
	void handleTransmitterUnsubscription(
			T_T_HighLevelCommunicationInterface communication, TransmitterDataUnsubscription unsubscription);

	/**
	 * Bestätigung einer ausgehenden Datenanmeldung auf einem entfernten datenverteiler
	 *
	 * @param communication Verbindung
	 * @param receipt       Telegram
	 */
	void handleTransmitterSubscriptionReceipt(
			T_T_HighLevelCommunicationInterface communication, TransmitterDataSubscriptionReceipt receipt);

	/**
	 * Es gibt einen neuen Weg, diese Nachricht wird im BestWayManager behandelt, siehe Dokumentation dort.
	 *
	 * @param communication  Verbindung über die das Telegram gesendet wurde
	 */
	void addWay(T_T_HighLevelCommunication communication);

	/**
	 * Wird bei einem BestWayUpdate-Telegramm aufgerufen
	 *
	 * @param communication             Verbindung über die das Telegram gesendet wurde
	 * @param transmitterBestWayUpdate Telegramm
	 */
	void updateBestWay(T_T_HighLevelCommunication communication, TransmitterBestWayUpdate transmitterBestWayUpdate);

	/**
	 * Wird bei jedem Login-Versuch aufgerufen und sorgt dafür, dass bei wiederholten Brute-Force-Angriffen der Login verzögert wird.
	 * @param passwordWasCorrect War das Passwort korrekt? Ausgebremst wird zwar immer, aber nur wenn das passwort falsch war, hat das eine Auswirkung auf folgende Login-Vesuche
	 */
	void throttleLoginAttempt(boolean passwordWasCorrect);
}
