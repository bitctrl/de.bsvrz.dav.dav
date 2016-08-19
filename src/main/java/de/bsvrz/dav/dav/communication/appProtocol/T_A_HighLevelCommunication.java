/*
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2005 by Kappich+Kniß Systemberatung Aachen (K2S)
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

package de.bsvrz.dav.dav.communication.appProtocol;

import de.bsvrz.dav.daf.communication.dataRepresentation.datavalue.SendDataObject;
import de.bsvrz.dav.daf.communication.lowLevel.HighLevelCommunicationCallbackInterface;
import de.bsvrz.dav.daf.communication.lowLevel.LowLevelCommunicationInterface;
import de.bsvrz.dav.daf.communication.lowLevel.telegrams.*;
import de.bsvrz.dav.daf.communication.protocol.UserLogin;
import de.bsvrz.dav.daf.communication.srpAuthentication.*;
import de.bsvrz.dav.daf.main.CommunicationError;
import de.bsvrz.dav.daf.main.InconsistentLoginException;
import de.bsvrz.dav.daf.main.authentication.ClientCredentials;
import de.bsvrz.dav.daf.main.config.ConfigurationChangeException;
import de.bsvrz.dav.daf.main.impl.CommunicationConstant;
import de.bsvrz.dav.dav.main.*;
import de.bsvrz.sys.funclib.debug.Debug;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Diese Klasse stellt eine Verbindung vom Datenverteiler zur Applikation dar. Über diese Verbindung können Telegramme an eine Applikation verschickt werden.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class T_A_HighLevelCommunication implements T_A_HighLevelCommunicationInterface, HighLevelCommunicationCallbackInterface {

	private static final Debug _debug = Debug.getLogger();

	/** Die erste Ebene der Kommunikation */
	private final LowLevelCommunicationInterface _lowLevelCommunication;

	/** Die Eigenschaften diese Verbindung */
	private ServerConnectionProperties _properties;

	/** Die unterstützten Versionen des Datenverteilers */
	private final Set<Integer> _supportedProtocolVersions = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(3, 4)));

	/** Der Applikation Id */
	private long _applicationId;

	/** Die Id des Benutzers */
	private UserLogin _userLogin = UserLogin.notAuthenticated();

	/** Der Konfiguration Id */
	private long _configurationId;

	/** Der Name der Applikation */
	private String _applicationName;

	/** Die Pid des Applikationstyps */
	private String _applicationTypePid;

	/** Die Pid der Konfiguration */
	private String _configurationPid;

	/** Die Authentifizierungskomponente */
	private AuthentificationComponent _authentificationComponent;

	/** Temporäre Liste der Systemtelegramme für interne Synchronisationszwecke. */
	private List<DataTelegram> _syncSystemTelegramList;

	/** Die Information ob auf die Konfiguration gewartet werden muss. */
	private boolean _waitForConfiguration;

	/** Objekt zur internen Synchronization */
	private final Object _sync;

	private boolean _closed = false;

	private Object _closedLock = new Object();

	private final long _connectionCreatedTime;

	private final HighLevelApplicationManager _applicationManager;

	private final LowLevelConnectionsManagerInterface _lowLevelConnectionsManager;

	/** Die Informationen zur SRP-Anmeldung */
	private SrpServerAuthentication _srpServerSession;

	/** Die SRP-Anfragedaten */
	private SrpRequest _srpRequest;

	/** 
	 * Geheimer datenverteilerseitig eindeutiger Zufallstext, aus dem SRP-Fake-Verifier gebildet werden können. Dieser Text wird vorberechnet, damit Fake-Verifier über die Laufzeit des
	 * Datenverteilers konstant sind und jemand so nicht einfach die Existenz von Benutzern prüfen kann
	 */
	private static final String _secretToken = new BigInteger(64, new SecureRandom()).toString(16);

	/**
	 * Kryptographische Parameter für die SRP-Authentifizierung
	 */
	private SrpCryptoParameter _srpCryptoParameter;
	private UserLogin _pendingSrpUserLogin;

	/**
	 * Erzeugt ein neues Objekt mit den gegebenen Parametern.
	 *
	 * @param properties           stellt die Parameter einer Verbindung zwischen zwei Servern
	 * @param lowLevelConnectionsManager
	 * @param waitForConfiguration true: ,false:
	 */
	public T_A_HighLevelCommunication(
			ServerConnectionProperties properties,
			HighLevelApplicationManager applicationManager, final LowLevelConnectionsManagerInterface lowLevelConnectionsManager, boolean waitForConfiguration) {
		_lowLevelConnectionsManager = lowLevelConnectionsManager;
		_applicationId = -1;
		_lowLevelCommunication = properties.getLowLevelCommunication();
		_properties = properties;
		_applicationManager = applicationManager;
		_authentificationComponent = _properties.getAuthentificationComponent();
		_syncSystemTelegramList = new LinkedList<DataTelegram>();
		_waitForConfiguration = waitForConfiguration;
		_sync = new Integer(hashCode());
		_connectionCreatedTime = System.currentTimeMillis();
		_lowLevelCommunication.setHighLevelComponent(this);
	}

	@Override
	public final long getTelegramTime(final long maxWaitingTime) throws CommunicationError {
		long time = System.currentTimeMillis();
		TelegramTimeRequest telegramTimeRequest = new TelegramTimeRequest(time);
		_lowLevelCommunication.send(telegramTimeRequest);

		TelegramTimeAnswer telegramTimeAnswer = null;
		long waitingTime = 0, startTime = System.currentTimeMillis();
		long sleepTime = 10;
		while(waitingTime < maxWaitingTime) {
			try {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.wait(sleepTime);
					if(sleepTime < 1000) sleepTime *= 2;
					DataTelegram telegram;
					ListIterator _iterator = _syncSystemTelegramList.listIterator(0);
					while(_iterator.hasNext()) {
						telegram = (DataTelegram)_iterator.next();
						if((telegram != null) && (telegram.getType() == DataTelegram.TELEGRAM_TIME_ANSWER_TYPE)) {
							if(((TelegramTimeAnswer)telegram).getTelegramStartTime() == time) {
								telegramTimeAnswer = (TelegramTimeAnswer)telegram;
								_iterator.remove();
								break;
							}
						}
					}
					if(telegramTimeAnswer != null) {
						break;
					}
				}
				waitingTime = System.currentTimeMillis() - startTime;
			}
			catch(InterruptedException ex) {
				ex.printStackTrace();
				throw new CommunicationError("Thread wurde unterbrochen.", ex);
			}
		}
		if(telegramTimeAnswer == null) {
			return -1;
		}
		return telegramTimeAnswer.getRoundTripTime();
	}

	@Override
	public final void sendData(final ApplicationDataTelegram telegram, final boolean toCentralDistributor) {
		_lowLevelCommunication.send(telegram);
	}

	public final void sendData(ApplicationDataTelegram telegram) {
		_lowLevelCommunication.send(telegram);
	}

	public final void sendData(ApplicationDataTelegram[] telegrams) {
		_lowLevelCommunication.send(telegrams);
	}

	@Override
	public final void terminate(final boolean error, final String message) {
		final DataTelegram terminationTelegram;
		if(error) {
			terminationTelegram = new TerminateOrderTelegram(message);
		}
		else {
			terminationTelegram = new ClosingTelegram();
		}
		terminate(error, message, terminationTelegram);
	}

	/**
	 * Zeitpunkt, an dem das Objekt erstellt wurde und somit eine Verbindung zum DaV bestand.
	 *
	 * @return Zeit in ms seit dem 1.1.1970
	 */
	public long getConnectionCreatedTime() {
		return _connectionCreatedTime;
	}

	/**
	 * Liefert einen beschreibenden Text mit dem Zustand des Sendepuffers aus der LowLevelCommunication.
	 *
	 * @return Sendepufferzustand als Text
	 *
	 * @see de.bsvrz.dav.daf.communication.lowLevel.LowLevelCommunicationInterface#getSendBufferState()
	 */
	public String getSendBufferState() {
		return _lowLevelCommunication.getSendBufferState();
	}

	public final void terminate(boolean error, String message, DataTelegram terminationTelegram) {
		synchronized(_closedLock) {
			if(_closed) return;
			_closed = true;
		}
		synchronized(this) {
			String debugMessage = "Verbindung zur Applikation (id: " + getId() + ", typ: " + getApplicationTypePid() + ", name: " + getApplicationName()
			                      + ") wird terminiert. Ursache: " + message;
			if(error) {
				_debug.error(debugMessage);
			}
			else {
				_debug.info(debugMessage);
			}
			if(_lowLevelCommunication != null) {
				_lowLevelCommunication.disconnect(error, message, terminationTelegram);
			}
			_applicationManager.removeApplication(this);
		}
	}


	@Override
	public void disconnected(boolean error, final String message) {
		terminate(error, message);
	}

	@Override
	public void updateConfigData(SendDataObject receivedData) {
		throw new UnsupportedOperationException("updateConfigData nicht implementiert");
	}

	@Override
	public final void triggerSender(BaseSubscriptionInfo data, byte state) {
		RequestSenderDataTelegram requestSenderDataTelegram = new RequestSenderDataTelegram(data, state);
		_lowLevelCommunication.send(requestSenderDataTelegram);
	}

	@Override
	public final long getId() {
		return _applicationId;
	}

	public final UserLogin getUserLogin() {
		return _userLogin;
	}

	/**
	 * Setzt den eingeloggten Benutzer (nur für Testzwecke)
	 * @param userLogin Benutzer
	 */
	public void setUserLogin(final UserLogin userLogin) {
		_userLogin = userLogin;
	}

	@Override
	public final long getConfigurationId() {
		return _configurationId;
	}

	@Override
	public final String getApplicationTypePid() {
		return _applicationTypePid;
	}

	@Override
	public final String getApplicationName() {
		return _applicationName;
	}

	@Override
	public final boolean isConfiguration() {
		if(CommunicationConstant.CONFIGURATION_TYPE_PID.equals(_applicationTypePid)) {
			Object[] objects = _properties.getLocalModeParameter();
			if(objects != null) {
				String configurationPid = (String)objects[0];
				if(_configurationPid.equals(configurationPid)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public final void continueAuthentication() {
		synchronized(_sync) {
			_waitForConfiguration = false;
			_sync.notify();
		}
	}


	/**
	 * Gibt die Version zurück, die von dieser Verbindung unterstützt wird.
	 *
	 * @param versions Versionen, die unterstützt werden sollen. Wird <code>null</code> übergeben, so wird -1 zurückgegeben.
	 *
	 * @return Version, die aus den gegebenen Versionen unterstützt wird. Wird keine der übergebenen Versionen unterstützt, so wird -1 zurückgegeben.
	 */
	private int getPreferredVersion(int[] versions) {
		if(versions == null) {
			return -1;
		}
		return IntStream.of(versions).filter(_supportedProtocolVersions::contains).max().orElse(-1);
	}

	@Override
	public final void update(DataTelegram telegram) {
		if(telegram == null) {
			return;
		}
		switch(telegram.getType()) {
			case DataTelegram.TELEGRAM_TIME_ANSWER_TYPE: {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			}
			case DataTelegram.PROTOCOL_VERSION_REQUEST_TYPE: {
				ProtocolVersionRequest protocolVersionRequest = (ProtocolVersionRequest)telegram;
				int version = getPreferredVersion(protocolVersionRequest.getVersions());
				ProtocolVersionAnswer protocolVersionAnswer = new ProtocolVersionAnswer(version);
				_lowLevelCommunication.send(protocolVersionAnswer);
				break;
			}
			case DataTelegram.AUTHENTIFICATION_TEXT_REQUEST_TYPE: {
				needsToBeNotAuthenticated();
				AuthentificationTextRequest authentificationTextRequest = (AuthentificationTextRequest) telegram;
				if(!firstInitialization(
						authentificationTextRequest.getApplicationTypePid(),
						authentificationTextRequest.getApplicationName(),
						authentificationTextRequest.getConfigurationPid()
				)) return;

				String text = _authentificationComponent.getAuthentificationText(_applicationName);
				_lowLevelCommunication.send(new AuthentificationTextAnswer(text));
				break;
			}
			case DataTelegram.AUTHENTIFICATION_REQUEST_TYPE: {
				needsToBeNotAuthenticated();

				AuthentificationRequest authentificationRequest = (AuthentificationRequest)telegram;
				String userName = authentificationRequest.getUserName();

				try {
					_userLogin = _lowLevelConnectionsManager.login(
							userName,
							authentificationRequest.getUserPassword(),
							_authentificationComponent.getAuthentificationText(_applicationName),
							_authentificationComponent.getAuthentificationProcess(),
							_applicationTypePid
					);

					// Brute-Force-Bremse
					_applicationManager.throttleLoginAttempt(_userLogin.isAuthenticated());
					
					completeAuthenticationAndSendAnswer();
				}
				catch(ConfigurationChangeException ex) {
					ex.printStackTrace();
					terminate(
							true, "Fehler während der Authentifizierung einer Applikation beim Zugriff auf die Konfiguration: " + ex.getMessage()
					);
					return;
				}
				break;
			}
			case DataTelegram.SRP_REQUEST_TYPE:
				needsToBeNotAuthenticated();
				_srpRequest = (SrpRequest) telegram;

				SrpVerifierAndUser srpVerifierAndUser;
				try {
					srpVerifierAndUser = fetchSrpUserData(_srpRequest.getUserName(), _srpRequest.getPasswordIndex());
				}
				catch(SrpNotSupportedException e) {
					// SRP wird von der Konfiguration nicht unterstützt
					_lowLevelCommunication.send(new SrpAnswer(e.getMessage()));
					return;
				}
				final SrpVerifierData srpVerifierData = srpVerifierAndUser.getVerifier();
				_pendingSrpUserLogin = srpVerifierAndUser.getUserLogin();
				_srpCryptoParameter = srpVerifierData.getSrpCryptoParameter();
				_srpServerSession = new SrpServerAuthentication(_srpCryptoParameter);
				final BigInteger b = _srpServerSession.step1(_srpRequest.getUserName(), srpVerifierData.getSalt(), srpVerifierData.getVerifier(), !_pendingSrpUserLogin
						.isAuthenticated());
				final SrpAnswer srpAnswer = new SrpAnswer(b, srpVerifierData.getSalt(), _srpCryptoParameter);
				_lowLevelCommunication.send(srpAnswer);
				break;
			case DataTelegram.SRP_VALDIATE_REQUEST_TYPE:
				needsToBeNotAuthenticated();
				
				final SrpValidateRequest srpValidateRequest = (SrpValidateRequest) telegram;
				if(_srpServerSession == null || _srpRequest == null){
					terminate(true, "Unerwartetes SRP-Telegramm");
					return;
				}
				try {
					final BigInteger m2 = _srpServerSession.step2(srpValidateRequest.getA(), srpValidateRequest.getM1());
					// Passwort ist korrekt
					
					// Brute-Force-Bremse
					_applicationManager.throttleLoginAttempt(true);
					
					final SrpValidateAnswer answer = new SrpValidateAnswer(m2);
					_lowLevelCommunication.sendDirect(answer);
                    if(_srpRequest.getPasswordIndex() != -1){
	                    // Einloggen erfolgreich, also Einmalpasswort deaktivieren
	                    _applicationManager.disableSingleServingPassword(_srpRequest.getUserName(), _srpRequest.getPasswordIndex());
                    }
					
					final BigInteger sessionKey = _srpServerSession.getSessionKey();
					
					_userLogin = _pendingSrpUserLogin;
					_lowLevelCommunication.enableEncryption(new SrpTelegramEncryption(SrpUtilities.bigIntegerToBytes(sessionKey), false, _srpCryptoParameter));
				}
				catch(InconsistentLoginException | SrpNotSupportedException ignored) {
					// Passwort ist falsch
					
					// Brute-Force-Bremse
					_applicationManager.throttleLoginAttempt(false);
					
					// Negative Quittung senden
					final SrpValidateAnswer answer = new SrpValidateAnswer(BigInteger.ZERO);
					_lowLevelCommunication.send(answer);
				}
				finally {
					// Bisherige SRP-Sitzung nicht weiterverwenden, Client muss im Falle einer falschen Passworteingabe einen neuen Request senden
					_srpServerSession = null;
				}
				break;
			case DataTelegram.DISABLE_ENCRYPTION_REQUEST_TYPE:
				needsToBeAuthenticated();
				if(_properties.getEncryptionPreference().shouldDisable(_lowLevelCommunication.getConnectionInterface().isLoopback())){
					_lowLevelCommunication.sendDirect(new DisableEncryptionAnswer(true));
					_lowLevelCommunication.disableEncryption();
				}
				else {
					_lowLevelCommunication.send(new DisableEncryptionAnswer(false));
				}
				break;
			case DataTelegram.APPLICATION_REQUEST_TYPE:
				needsToBeAuthenticated();
				try {
					ApplicationRequest applicationRequest = (ApplicationRequest) telegram;
					if(!firstInitialization(
							applicationRequest.getApplicationTypePid(),
							applicationRequest.getApplicationName(),
							applicationRequest.getConfigurationPid()
					)) return;

					completeAuthenticationAndSendAnswer();
				}
				catch(ConfigurationChangeException ex) {
					ex.printStackTrace();
					terminate(
							true, "Fehler während der Authentifizierung einer Applikation beim Zugriff auf die Konfiguration: " + ex.getMessage()
					);
					return;
				}
				break;
			case DataTelegram.COM_PARAMETER_REQUEST_TYPE: {
				ComParametersRequest comParametersRequest = (ComParametersRequest)telegram;
				// Empfangene Timeoutparameter werden übernommen und nach unten begrenzt
				long keepAliveSendTimeOut = comParametersRequest.getKeepAliveSendTimeOut();
				if(keepAliveSendTimeOut < 5000) keepAliveSendTimeOut = 5000;
				long keepAliveReceiveTimeOut = comParametersRequest.getKeepAliveReceiveTimeOut();
				if(keepAliveReceiveTimeOut < 6000) keepAliveReceiveTimeOut = 6000;

				ComParametersAnswer comParametersAnswer;

				byte cacheThresholdPercentage = comParametersRequest.getCacheThresholdPercentage();
				short flowControlThresholdTime = comParametersRequest.getFlowControlThresholdTime();
				int minConnectionSpeed = comParametersRequest.getMinConnectionSpeed();
				comParametersAnswer = new ComParametersAnswer(
						keepAliveSendTimeOut, keepAliveReceiveTimeOut, cacheThresholdPercentage, flowControlThresholdTime, minConnectionSpeed
				);
				_lowLevelCommunication.send(comParametersAnswer);
				_lowLevelCommunication.updateKeepAliveParameters(keepAliveSendTimeOut, keepAliveReceiveTimeOut);
				_lowLevelCommunication.updateThroughputParameters(
						(float)cacheThresholdPercentage * 0.01f, (long)(flowControlThresholdTime * 1000), minConnectionSpeed
				);
				// locale Configuration
				if(CommunicationConstant.CONFIGURATION_TYPE_PID.equals(_applicationTypePid)) {
					Object[] objects = _properties.getLocalModeParameter();
					if(objects != null) {
						String configurationPid = (String)objects[0];
						if(_configurationPid.equals(configurationPid)) {
							_lowLevelConnectionsManager.setLocalConfigurationAvailable();
						}
					}
				}
				break;
			}
			case DataTelegram.TELEGRAM_TIME_REQUEST_TYPE: {
				TelegramTimeRequest telegramTimeRequest = (TelegramTimeRequest)telegram;
				_lowLevelCommunication.send(new TelegramTimeAnswer(telegramTimeRequest.getTelegramRequestTime()));
				break;
			}
			case DataTelegram.SEND_SUBSCRIPTION_TYPE: {
				needsToBeAuthenticated();
				SendSubscriptionTelegram sendSubscriptionTelegram = (SendSubscriptionTelegram)telegram;
				_applicationManager.handleSendSubscription(this, sendSubscriptionTelegram);
				break;
			}
			case DataTelegram.SEND_UNSUBSCRIPTION_TYPE: {
				needsToBeAuthenticated();
				SendUnsubscriptionTelegram sendUnsubscriptionTelegram = (SendUnsubscriptionTelegram)telegram;
				_applicationManager.handleSendUnsubscription(this, sendUnsubscriptionTelegram);
				break;
			}
			case DataTelegram.RECEIVE_SUBSCRIPTION_TYPE: {
				needsToBeAuthenticated();
				ReceiveSubscriptionTelegram receiveSubscriptionTelegram = (ReceiveSubscriptionTelegram)telegram;
				_applicationManager.handleReceiveSubscription(this, receiveSubscriptionTelegram);
				break;
			}
			case DataTelegram.RECEIVE_UNSUBSCRIPTION_TYPE: {
				needsToBeAuthenticated();
				ReceiveUnsubscriptionTelegram receiveUnsubscriptionTelegram = (ReceiveUnsubscriptionTelegram)telegram;
				_applicationManager.handleReceiveUnsubscription(this, receiveUnsubscriptionTelegram);
				break;
			}
			case DataTelegram.APPLICATION_DATA_TELEGRAM_TYPE: {
				needsToBeAuthenticated();
				ApplicationDataTelegram applicationDataTelegram = (ApplicationDataTelegram)telegram;
				_applicationManager.handleDataTelegram(this, applicationDataTelegram);
				break;
			}
			case DataTelegram.TERMINATE_ORDER_TYPE: {
				TerminateOrderTelegram terminateOrderTelegram = (TerminateOrderTelegram)telegram;
				terminate(true, "Verbindung wurde von der Applikation terminiert. Ursache: " + terminateOrderTelegram.getCause(), null);
				break;
			}
			case DataTelegram.CLOSING_TYPE: {
				terminate(false, "Verbindung wurde von der Applikation geschlossen", null);
				break;
			}
			case DataTelegram.KEEP_ALIVE_TYPE: {
				break;
			}
			default: {
				System.out.println(telegram);
				break;
			}
		}
	}

	/**
	 * Hilfsfunktion, die eine Exception wirft, wenn der Benutzer noch nicht erfolgreich authentifiziert ist
	 */
	private void needsToBeAuthenticated() {
		if(!_userLogin.isAuthenticated()) {
			throw new IllegalStateException("Benutzer ist nicht authentifiziert");
		}
	}

	/**
	 * Hilfsfunktion, die eine Exception wirft, wenn der Benutzer schon erfolgreich authentifiziert ist
	 */
	private void needsToBeNotAuthenticated() {
		if(_userLogin.isAuthenticated()) {
			throw new IllegalStateException("Benutzer ist bereits authentifiziert");
		}
	}

	/**
	 * Bestimmt für den Benutzernamen und übergebenen Einmal-Passwortindex (bzw -1 für Standardpasswort) den Srp-Verifier und die Benutzer-ID.
	 * Hierzu wird normalerweise die Konfiguration gefragt, aber für die Anmeldung der lokalen Konfiguration, Parametrierung und der
	 * {@link SelfClientDavConnection} gibt es Spezialfälle.
	 * @param userName Benutzername
	 * @param passwordIndex Einmalpasswort-Index
	 * @return SRP-Überprüfungscode
	 * @throws SrpNotSupportedException SRP wird von der Konfiguration nicht unterstützt
	 */
	private SrpVerifierAndUser fetchSrpUserData(final String userName, final int passwordIndex) throws SrpNotSupportedException {
	
		// Spezialfälle für lokale Konfiguration, Parametrierung und SelfClientDafConnection wie in LowLevelAuthentication.isValidUser():
		ServerDavParameters serverDavParameters = _lowLevelConnectionsManager.getServerDavParameters();
		if(userName.equals(serverDavParameters.getConfigurationUserName())) {
			// Spezialfall lokale Konfiguration. Hier muss das Passwort aus der passwd genommen werden, da keine Konfiguration gefragt werden kann
			return new SrpVerifierAndUser(UserLogin.systemUser(), fakeVerifier(serverDavParameters.getConfigurationClientCredentials(), userName), true);
		}
		else if(userName.equals(serverDavParameters.getParameterUserName())) {
			// Spezialfall Parametrierung. Hier kann das Passwort wie bei der Konfiguration aus der passwd genommen werden, da dies historisch so realisiert war.
			ClientCredentials parameterClientCredentials = serverDavParameters.getParameterClientCredentials();
			if(parameterClientCredentials != null) {
				return new SrpVerifierAndUser(UserLogin.systemUser(), fakeVerifier(parameterClientCredentials, userName), true);
			}
		}
		else if(userName.equals(_lowLevelConnectionsManager.getClientDavParameters().getUserName())) {
			if(serverDavParameters.isLocalMode()) {
				return new SrpVerifierAndUser(
						UserLogin.systemUser(),
						fakeVerifier(_lowLevelConnectionsManager.getClientDavParameters().getClientCredentials(), userName),
						true
				);
			}
		}

		// Ansonsten die Konfiguration fragen
		if(_waitForConfiguration) {
			// Ggf warten bis Konfiguration verfügbar
			synchronized(_sync) {
				try {
					while(_waitForConfiguration) {
						if(_closed) throw new IllegalStateException("Die Konfiguration hat sich beendet");
						_sync.wait(1000);
					}
				}
				catch(InterruptedException ex) {
					throw new IllegalStateException("Unterbrochen beim Warten auf Konfiguration", ex);
				}
			}
		}
		_waitForConfiguration = false;

		// Eigentlichen Überprüfungscode abfragen
		SrpVerifierAndUser srpVerifierAndUser = _applicationManager.fetchSrpVerifierAndAuthentication(userName, passwordIndex);

		if(userName.equals(serverDavParameters.getParameterUserName()) && srpVerifierAndUser.getUserLogin().isAuthenticated()) {
			// Spezialfall: die Parametrierung kriegt volle Rechte, also BenutzerID auf 0 setzen
			return new SrpVerifierAndUser(UserLogin.systemUser(), srpVerifierAndUser.getVerifier(), srpVerifierAndUser.isPlainTextPassword());
		}
		
		return srpVerifierAndUser;
	}

	private static SrpVerifierData fakeVerifier(final ClientCredentials clientCredentials, final String userName) {
		return SrpClientAuthentication.createVerifier(SrpCryptoParameter.getDefaultInstance(), userName, clientCredentials, secretHash(userName));
	}

	private static byte[] secretHash(final String userName) {
		return SrpUtilities.generatePredictableSalt(SrpCryptoParameter.getDefaultInstance(), (userName + _secretToken).getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String toString() {
		if(_applicationName != null) {
			return _applicationName + " [" + _applicationId + "]";
		}
		return "[" + _applicationId + "]";

	}

	/**
	 * Prüft ob die vorangegangene Authentifizierung erfolgreich war. Ist dies der Fall, wird die Initialisierung abgeschlossen und ggf. ein Applikationsobjekt bei der Konfiguration angefordert.
	 * 
	 * Schickt eine Antwort über den Authentifizierungs-Erfolg an den Client. 
	 * 
	 * @throws ConfigurationChangeException Fehler beim Anlegen eines Applikationsobjekts
	 */
	private void completeAuthenticationAndSendAnswer() throws ConfigurationChangeException {
		AuthentificationAnswer authentificationAnswer;
		if(_userLogin.isAuthenticated()) {
			// Authentifizierung ist erfolgreich
			
			if(!updateParametersAndCreateApplicationObject()) return;
			
			authentificationAnswer = new AuthentificationAnswer(
					_userLogin.toLong(), _applicationId, _configurationId, _properties.getDataTransmitterId()
			);
		}
		else {
			authentificationAnswer = new AuthentificationAnswer(false);
		}
		_lowLevelCommunication.send(authentificationAnswer);
	}

	/**
	 * Speichert die bei der Authentifizierung übertragenen Daten zwischen und wartet ggf. auf die Konfiguration. Da zu diesem Zeitpunkt noch keine
	 * Authentifizierung erfolgt ist, dürfen diese Daten noch nicht an relevanten Stellen weiterverwendet werden
	 * @param applicationTypePid Pid des verbundenen Applikationstyps (z.B. "typ.applikation")
	 * @param applicationName Name der verbundenen Applikation
	 * @param configurationPid Bei der lokalen Konfiguration: "Pid:ID" des KV, sonst {@link CommunicationConstant#LOCALE_CONFIGURATION_PID_ALIASE}
	 * @return true wenn warten auf Konfiguration erfolgreich, sonst false
	 */
	private boolean firstInitialization(final String applicationTypePid, final String applicationName, final String configurationPid) {
		if(configurationPid.isEmpty()) {
			_configurationPid = CommunicationConstant.LOCALE_CONFIGURATION_PID_ALIASE;
		}
		else {
			_configurationPid = configurationPid;
		}
		_applicationName = applicationName;
		_applicationTypePid = applicationTypePid;
		
		_debug.finest("applicationName", _applicationName);
		_debug.finest("applicationTypePid", _applicationTypePid);
		
		_lowLevelCommunication.setRemoteName(_applicationName + " (Typ: " + _applicationTypePid + ")");
		return initializeConfiguration();
	}

	/**
	 * Aktualisiert verschiedene Parameter und legt ein Applikationsobjekt an
	 * @return true falls erfolgreich, sonst false
	 * @throws ConfigurationChangeException Problem beim Anlegen des Applikationsobjekts
	 */
	private boolean updateParametersAndCreateApplicationObject() throws ConfigurationChangeException {
		// Pid und Id der Default-Konfiguration aus globalen Einstellungen holen und in lokalen Einstellungen speichern
		if(_properties.isLocalMode()) {
			String pid = _lowLevelConnectionsManager.getLocalModeConfigurationPid();
			long id = _lowLevelConnectionsManager.getLocalModeConfigurationId();
			_properties.setLocalModeParameter(pid, id);
		}
		if(CommunicationConstant.LOCALE_CONFIGURATION_PID_ALIASE.equals(_configurationPid)) {
			Object[] objects = _properties.getLocalModeParameter();
			if(objects != null) {
				_configurationPid = (String)objects[0];
				_configurationId = (Long) objects[1];
			}
			else {
				_configurationId = _applicationManager.getConfigurationId(_configurationPid);
			}
		}
		else {
			_configurationId = _applicationManager.getConfigurationId(_configurationPid);
		}

		if(_configurationId == -1) {
			terminate(true, "Ungültige Pid der Konfiguration: " + _configurationPid);
			return false;
		}
		
		if(!createApplicationObject()) return false;
		
		_lowLevelConnectionsManager.updateApplicationId(this);

		return true;
	}

	/**
	 * Legt ein Applikationsobjekt an (falls es sich nicht um die lokale Konfiguration handelt)
	 * @return true falls erfolgreich, sonst (z.B. bei unbekanntem Typ) false
	 * @throws ConfigurationChangeException
	 */
	private boolean createApplicationObject() throws ConfigurationChangeException {
		if(CommunicationConstant.CONFIGURATION_TYPE_PID.equals(_applicationTypePid)) {
			Object[] objects = _properties.getLocalModeParameter();
			if(objects == null) {
				_applicationId = _applicationManager.createNewApplication(this, _applicationTypePid, _applicationName);
			}
			else {
				String configurationPid = (String)objects[0];
				if(this._configurationPid.equals(configurationPid)) {
					_applicationId = 0;
				}
				else {
					_applicationId = _applicationManager.createNewApplication(this, _applicationTypePid, _applicationName);
				}
			}
		}
		else {
			_applicationId = _applicationManager.createNewApplication(this, _applicationTypePid, _applicationName);
		}

		if(_applicationId == -1) {
			terminate(
					true,
					"Die Id der Applikation konnte nicht ermittelt werden, ApplikationsTyp: " + _applicationTypePid + ", ApplikationsName: "
							+ _applicationName
			);
			return false;
		}
		return true;
	}

	/** 
	 * Gibt die prägende ID der Konfiguration zurück
	 * @return die prägende ID der Konfiguration, oder 0 falls es sich um eine normale Applikation handelt
	 */
	private long getFormativeConfigurationId() {
		long formativeConfigurationId = 0;
		if("".equals(_configurationPid)) {
			_configurationPid = CommunicationConstant.LOCALE_CONFIGURATION_PID_ALIASE;
		}
		else {
			String[] strings = _configurationPid.split(":");
			if(strings.length > 1) {
				_configurationPid = strings[0];
				try {
					// Id des Konfigurationsverantwortlichen wird mit Doppelpunkt getrennt hinter der Pid erwartet,
					// wenn sich die Konfiguration anmeldet
					formativeConfigurationId = Long.parseLong(strings[1]);
				}
				catch(NumberFormatException e) {
					_debug.error("Fehler beim Parsen der mit Doppelpunkt getrennten Id an der Pid des Konfigurationsverantwortlichen", e);
				}
			}
		}
		return formativeConfigurationId;
	}

	/**
	 * Diese Methode prüft, ob es schon eine Konfiguration gibt. Falls ja, gibt die Methode true zurück. Falls nein wird geprüft ob es sich bei dieser
	 * Applikation um die Konfiguration handelt. Falls ja, wird der Datenverteiler mit dieser Konfiguration geprägt. Falls nein wird auf die Konfiguration gewartet.
	 * @return true: Erfolgreich initialisiert, false: Timeout oder sonstiger Fehler
	 */
	private boolean initializeConfiguration() {
		if(!_waitForConfiguration) {
			return true;
		}
		boolean mustWait = true;
		if(CommunicationConstant.CONFIGURATION_TYPE_PID.equals(_applicationTypePid) && _properties.isLocalMode()) {
			// Es handelt sich um eine Konfiguration und der Datenverteiler wartet auf eine
			final long formativeConfigurationId = getFormativeConfigurationId();
			if(formativeConfigurationId != 0) {
				// Die von der Konfiguration vorgegebene Pid und Id des Konfigurationsverantwortlichen wird als Default für die Applikationen
				// gespeichert
				_properties.setLocalModeParameter(_configurationPid, formativeConfigurationId);
				_lowLevelConnectionsManager.setLocalModeParameter(_configurationPid, formativeConfigurationId);
				_debug.info("Default-Konfiguration " + _configurationPid + ", Id " + formativeConfigurationId);
				mustWait = false;
			}
			else {
				terminate(true, "Konfiguration hat die Id des Konfigurationsverantwortlichen nicht vorgegeben");
				return false;
			}
		}
		if(mustWait) {
			synchronized(_sync) {
				try {
					_debug.finest("mustWait", mustWait);
					while(_waitForConfiguration) {
						if(_closed) return false;
						_sync.wait(1000);
					}
				}
				catch(InterruptedException ex) {
					return false;
				}
			}
		}
		_waitForConfiguration = false;
		return true;
	}
}
