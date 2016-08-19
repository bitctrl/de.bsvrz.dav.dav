/*
 * Copyright 2009 by Kappich Systemberatung, Aachen
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2004 by Kappich+Kniß Systemberatung, Aachen
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

package de.bsvrz.dav.dav.communication.davProtocol;

import de.bsvrz.dav.daf.communication.dataRepresentation.datavalue.SendDataObject;
import de.bsvrz.dav.daf.communication.lowLevel.ConnectionInterface;
import de.bsvrz.dav.daf.communication.lowLevel.HighLevelCommunicationCallbackInterface;
import de.bsvrz.dav.daf.communication.lowLevel.LowLevelCommunicationInterface;
import de.bsvrz.dav.daf.communication.lowLevel.telegrams.*;
import de.bsvrz.dav.daf.communication.protocol.UserLogin;
import de.bsvrz.dav.daf.communication.srpAuthentication.*;
import de.bsvrz.dav.daf.main.CommunicationError;
import de.bsvrz.dav.daf.main.EncryptionStatus;
import de.bsvrz.dav.daf.main.InconsistentLoginException;
import de.bsvrz.dav.daf.main.authentication.ClientCredentials;
import de.bsvrz.dav.daf.main.config.ConfigurationException;
import de.bsvrz.dav.daf.main.impl.CommunicationConstant;
import de.bsvrz.dav.daf.util.Longs;
import de.bsvrz.dav.dav.main.*;
import de.bsvrz.dav.dav.subscriptions.RemoteCentralSubscription;
import de.bsvrz.dav.dav.subscriptions.RemoteSourceSubscription;
import de.bsvrz.dav.dav.subscriptions.RemoteSubscription;
import de.bsvrz.sys.funclib.debug.Debug;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.IntStream;


/**
 * Diese Klasse stellt die Funktionalitäten für die Kommunikation zwischen zwei Datenverteilern zur Verfügung. Hier wird die Verbindung zwischen zwei DAV
 * aufgebaut, sowie die Authentifizierung durchgeführt.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class T_T_HighLevelCommunication implements T_T_HighLevelCommunicationInterface, HighLevelCommunicationCallbackInterface {

	private static final Debug _debug = Debug.getLogger();

	/** Die Id des über diesen Kanal verbundenen Datenverteiler */
	private long _connectedTransmitterId;

	/** Die Id des Remotebenutzers */
	private UserLogin _userLogin = UserLogin.notAuthenticated();

	/** Die erste Ebene der Kommunikation */
	private LowLevelCommunicationInterface _lowLevelCommunication;

	/** Die Eigenschaften dieser Verbindung */
	private ServerConnectionProperties _properties;

	/** Die unterstützten Versionen des Datenverteilers */
	private final Set<Integer> _supportedProtocolVersions = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(2, 3)));
	
	/** Die Version, mit der die Kommunikation erfolgt */
	private int _version;

	/** Die Authentifizierungskomponente */
	private AuthentificationComponent _authentificationComponent;

	/** Temporäre Liste der Systemtelegramme für interne Synchronisationszwecke. */
	private final LinkedList<DataTelegram> _syncSystemTelegramList;

	/** Temporäre Liste der Telegramme, die vor die Initialisierung eingetroffen sind. */
	private final LinkedList<DataTelegram> _fastTelegramsList;

	/** Gewichtung dieser Verbindung */
	private short _weight;

	/**
	 * Signalisiert, ob die Initialisierungsphase abgeschlossen ist
	 */
	private boolean _initComplete = false;

	/** Die Information ob auf die Konfiguration gewartet werden muss. */
	private boolean _waitForConfiguration;

	/** Objekt zur internen Synchronization */
	private Integer _sync;

	/** Objekt zur internen Synchronization */
	private Integer _authentificationSync;

	/** Legt fest, ob es sich um eine eingehende Verbindung handelt (dieser Datenverteiler also der Server ist). */
	private boolean _isIncomingConnection;

	/** Signalisiert dass diese Verbindung terminiert ist */
	private volatile boolean _closed = false;

	private Object _closedLock = new Object();

	/** Benutzername mit dem sich dieser Datenverteiler beim anderen Datenverteiler authentifizieren soll */
	private String _userForAuthentication;

	/** Passwort des Benutzers mit dem sich dieser Datenverteiler beim anderen Datenverteiler authentifizieren soll */
	private ClientCredentials _credentialsForAuthentication;

	private final HighLevelTransmitterManagerInterface _transmitterManager;

	private final LowLevelConnectionsManagerInterface _lowLevelConnectionsManager;

	/** Status der Verbindung */
	private CommunicationStateAndMessage _state = new CommunicationStateAndMessage("", CommunicationState.Connecting, EncryptionStatus.notEncrypted(), "");
	
	private SrpRequest _srpRequest;
	private SrpServerAuthentication _srpServerSession;

	/**
	 * Die als Server bei der SRP-Authentifizierung (von der lokalen Konfiguration) empfangenen kryptographischen Parameter
	 */
	private SrpCryptoParameter _serverCryptoParams;

	private UserLogin _pendingSrpUserLogin;

	/**
	 * Erzeugt ein neues Objekt mit den gegebenen Parametern.
	 *  @param properties                 Eigenschaften dieser Verbindung
	 * @param lowLevelConnectionsManager Low-Level-Verbindugnsverwaltung
	 * @param weight                     Gewichtung dieser Verbindung
	 * @param waitForConfiguration       true: auf die KOnfiguration muss gewartet werden, false: Konfiguration ist vorhanden
	 * @param userForAuthentication           Benutzername mit dem sich dieser Datenverteiler beim anderen Datenverteiler authentifizieren soll
	 * @param credentialsForAuthentication     Passwort des Benutzers mit dem sich dieser Datenverteiler beim anderen Datenverteiler authentifizieren soll
	 * @param incomingConnection
	 */
	public T_T_HighLevelCommunication(
			ServerConnectionProperties properties,
			HighLevelTransmitterManagerInterface transmitterManager,
			final LowLevelConnectionsManagerInterface lowLevelConnectionsManager,
			short weight,
			boolean waitForConfiguration,
			final String userForAuthentication,
			final ClientCredentials credentialsForAuthentication,
			final boolean incomingConnection) {
		_transmitterManager = transmitterManager;
		_lowLevelConnectionsManager = lowLevelConnectionsManager;
		_credentialsForAuthentication = credentialsForAuthentication;
		_userForAuthentication = userForAuthentication;
		_connectedTransmitterId = -1;
		_weight = weight;
		_properties = properties;
		_lowLevelCommunication = _properties.getLowLevelCommunication();
		_authentificationComponent = _properties.getAuthentificationComponent();
		_syncSystemTelegramList = new LinkedList<DataTelegram>();
		_fastTelegramsList = new LinkedList<DataTelegram>();
		_waitForConfiguration = waitForConfiguration;
		_sync = hashCode();
		_authentificationSync = hashCode();
		_isIncomingConnection = incomingConnection;
		_lowLevelCommunication.setHighLevelComponent(this);
	}

	/**
	 * Diese Methode wird von der Verbindungsverwaltung aufgerufen, um eine logische Verbindung zwischen zwei Datenverteilern herzustellen. Zunächst wird die
	 * Protokollversion verhandelt. In einem Systemtelegramm ?TransmitterProtocolVersionRequest? werden die unterstützten Versionen über die Telegrammverwaltung
	 * an den zweiten Datenverteiler gesendet. Auf die Antwort wird eine gewisse Zeit gewartet (maximale Wartezeit auf synchrone Antworten). Wenn die Antwort
	 * innerhalb diese Zeit nicht angekommen bzw. keine der Protokollversionen vom anderen Datenverteiler unterstützt wird, wird eine CommunicationErrorAusnahme
	 * erzeugt. <br>Danach erfolgt die Authentifizierung: Über die Telegrammverwaltung wird ein Telegramm? TransmitterAuthentificationTextRequest? zum anderen
	 * Datenverteiler gesendet, um einen Schlüssel für die Authentifizierung anzufordern. Die ID des sendenden Datenverteilers wird den
	 * ServerConnectionProperties entnommen. Auf die Antwort ?TransmitterAuthentificationTextAnswer? wird eine gewisse Zeit gewartet (maximale Wartezeit auf
	 * synchrone Antworten). Wenn die Antwort nicht innerhalb dieser Zeit angekommen ist, wird eine CommunicationError-Ausnahme erzeugt. Das Passwort, welches
	 * in den ServerConnectionProperties spezifiziert ist, wird mit diesem Schlüssel und dem spezifizierten Authentifizierungsverfahren verschlüsselt. Aus dem
	 * Authentifizierungsverfahrennamen, dem verschlüsselten Passwort und dem Benutzernamen wird ein ?TransmitterAuthentificationRequest?-Telegramm gebildet und
	 * mittels Telegrammverwaltung zum anderen Datenverteiler gesendet. Auf die Antwort ?TransmitterAuthentificationAnswer? wird eine gewisse Zeit gewartet
	 * (maximale Wartezeit auf synchrone Antworten). Wenn die Antwort nicht innerhalb dieser Zeit angekommen ist oder konnte die Authentifizierung nicht
	 * erfolgreich abgeschlossen werden, so wird eine CommunicationError-Ausnahme erzeugt <br>Danach geht diese Methode geht in den Wartezustand, bis der andere
	 * Datenverteiler sich in umgekehrter Richtung auch erfolgreich authentifiziert hat. Dabei durchläuft der andere Datenverteiler das gleiche Prozedere wie
	 * zuvor beschrieben. <br>Im nächsten Schritt verhandeln die Datenverteiler die Keep-alive-Parameter und die Durchsatzprüfungsparameter
	 * (Verbindungsparameter). Ein ?TransmitterComParametersRequest? wird zum anderen Datenverteiler gesendet. Auch hier wird eine gewisse Zeit auf die Antwort
	 * ?TransmitterComParametersAnswer? gewartet (maximale Wartezeit auf synchrone Antworten). Wenn die Antwort nicht innerhalb dieser Zeit angekommen ist, wird
	 * eine CommunicationError-Ausnahme erzeugt. Sonst ist der Verbindungsaufbau erfolgreich abund der Austausch von Daten kann sicher durchgeführt werden.
	 *
	 * @throws CommunicationError , wenn bei der initialen Kommunikation mit dem Datenverteiler Fehler aufgetreten sind
	 */
	public final void connect() throws CommunicationError {
		_syncSystemTelegramList.clear();
		// Protokollversion verhandeln
		TransmitterProtocolVersionRequest protocolVersionRequest = new TransmitterProtocolVersionRequest(_supportedProtocolVersions.stream().mapToInt(x->x).toArray());
		sendTelegram(protocolVersionRequest);

		TransmitterProtocolVersionAnswer protocolVersionAnswer = (TransmitterProtocolVersionAnswer) waitForAnswerTelegram(
				DataTelegram.TRANSMITTER_PROTOCOL_VERSION_ANSWER_TYPE, "Antwort auf Verhandlung der Protokollversionen"
		);
		_version = protocolVersionAnswer.getPreferredVersion();
		if(_version == -1) {
			throw new CommunicationError("Die Protokollversionen " + _supportedProtocolVersions + " werden vom Datenverteiler nicht unterstützt.");
		}
		else if(!_supportedProtocolVersions.contains(_version)) {
			throw new CommunicationError("Die vom Datenverteiler vorgegebene Protokollversion (" + _version + ") wird lokal nicht unterstützt.");
		}

		authenticate();

		synchronized(_authentificationSync) {
			try {
				while(!_userLogin.isAuthenticated()) {
					if(_closed) return;
					_authentificationSync.wait(1000);
				}
			}
			catch(InterruptedException ex) {
				ex.printStackTrace();
				return;
			}
		}

		// Timeouts Parameter verhandeln
		TransmitterComParametersRequest comParametersRequest = new TransmitterComParametersRequest(
				_properties.getKeepAliveSendTimeOut(), _properties.getKeepAliveReceiveTimeOut()
		);
		sendTelegram(comParametersRequest);
		TransmitterComParametersAnswer comParametersAnswer = (TransmitterComParametersAnswer) waitForAnswerTelegram(
				DataTelegram.TRANSMITTER_COM_PARAMETER_ANSWER_TYPE, "Antwort auf Verhandlung der Kommunikationsparameter"
		);
		_lowLevelCommunication.updateKeepAliveParameters(
				comParametersAnswer.getKeepAliveSendTimeOut(), comParametersAnswer.getKeepAliveReceiveTimeOut()
		);
	}

	public LowLevelConnectionsManagerInterface getLowLevelConnectionsManager() {
		return _lowLevelConnectionsManager;
	}

	/** @return Liefert <code>true</code> zurück, falls die Verbindung geschlossen wurde, sonst <code>false</code>. */
	public boolean isClosed() {
		return _closed;
	}


	private DataTelegram waitForAnswerTelegram(final byte telegramType, final String descriptionOfExpectedTelegram) throws CommunicationError {
		long waitingTime = 0;
		long startTime = System.currentTimeMillis();
		long sleepTime = 10;
		final String expected = (" Erwartet wurde: " + descriptionOfExpectedTelegram);
		while(waitingTime < CommunicationConstant.MAX_WAITING_TIME_FOR_SYNC_RESPONCE) {
			try {
				synchronized(_syncSystemTelegramList) {
					if(_closed) throw new CommunicationError("Verbindung terminiert." + expected);
					_syncSystemTelegramList.wait(sleepTime);
					if(sleepTime < 1000) sleepTime *= 2;
					ListIterator<DataTelegram> iterator = _syncSystemTelegramList.listIterator(0);
					while(iterator.hasNext()) {
						final DataTelegram telegram = iterator.next();
						if(telegram != null) {
							if(telegram.getType() == telegramType) {
								iterator.remove();
								return telegram;
							}
							else {
								System.out.println(telegram.parseToString());
							}
						}
					}
				}
				waitingTime = System.currentTimeMillis() - startTime;
			}
			catch(InterruptedException ex) {
				throw new CommunicationError("Interrupt." + expected);
			}
		}
		throw new CommunicationError("Der Datenverteiler antwortet nicht." + expected);
	}


	@Override
	public final long getTelegramTime(final long maxWaitingTime) throws CommunicationError {
		long time = System.currentTimeMillis();
		TransmitterTelegramTimeRequest telegramTimeRequest = new TransmitterTelegramTimeRequest(time);
		sendTelegram(telegramTimeRequest);

		TransmitterTelegramTimeAnswer telegramTimeAnswer = null;
		long waitingTime = 0, startTime = System.currentTimeMillis();
		long sleepTime = 10;
		while(waitingTime < maxWaitingTime) {
			try {
				synchronized(_syncSystemTelegramList) {
					if(_closed)
						throw new CommunicationError("Verbindung terminiert. Erwartet wurde: Antwort auf eine Telegrammlaufzeitermittlung");
					_syncSystemTelegramList.wait(sleepTime);
					if(sleepTime < 1000) sleepTime *= 2;
					ListIterator<DataTelegram> iterator = _syncSystemTelegramList.listIterator(0);
					while(iterator.hasNext()) {
						final DataTelegram telegram = iterator.next();
						if((telegram != null) && (telegram.getType() == DataTelegram.TRANSMITTER_TELEGRAM_TIME_ANSWER_TYPE)) {
							if(((TransmitterTelegramTimeAnswer) telegram).getTelegramStartTime() == time) {
								telegramTimeAnswer = (TransmitterTelegramTimeAnswer) telegram;
								iterator.remove();
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
	public final long getRemoteNodeId() {
		return _connectedTransmitterId;
	}

	@Override
	public final int getThroughputResistance() {
		return _weight;
	}

	@Override
	public final void sendRoutingUpdate(RoutingUpdate[] routingUpdates) {
		if(routingUpdates == null) {
			throw new IllegalArgumentException("Argument ist null");
		}
		sendTelegram(new TransmitterBestWayUpdate(routingUpdates));
	}

	public final UserLogin getUserLogin() {
		return _userLogin;
	}

	@Override
	public final long getId() {
		return _connectedTransmitterId;
	}

	private void setCommunicationState(final CommunicationState communicationState, final String message) {
		_state = new CommunicationStateAndMessage(getRemoteAdress() + ":" + getRemoteSubadress(), communicationState, getEncryptionStatus(), message);
		_lowLevelConnectionsManager.updateCommunicationState();
	}

	public EncryptionStatus getEncryptionStatus() {
		return _lowLevelCommunication.getEncryptionStatus();
	}

	/**
	 * Gibt die Information zurück, ob diese Verbindung von dem anderen Datenverteiler aufgebaut wurde.
	 *
	 * @return true: Verbindung wurde vom anderen Datenverteiler aufgebaut und von diesem akzeptiert (Dieser Datenverteiler ist der "Server", der auf eingehende
	 * Verbindungen wartet). false: Dieser Datenverteiler hat die Verbindung aktiv aufgebaut, der andere Datenverteiler ist der "Server", der auf eingehende
	 * Verbindungen wartet.
	 */
	public final boolean isIncomingConnection() {
		return _isIncomingConnection;
	}

	/** @return  */
	public final String getRemoteAdress() {
		if(_lowLevelCommunication == null) {
			return null;
		}
		ConnectionInterface connection = _lowLevelCommunication.getConnectionInterface();
		if(connection == null) {
			return null;
		}
		return connection.getMainAdress();
	}


	/**
	 * Diese Methode gibt die Subadresse des Kommunikationspartners zurück.
	 *
	 * @return die Subadresse des Kommunikationspartners
	 */
	public final int getRemoteSubadress() {
		if(_lowLevelCommunication == null) {
			return -1;
		}
		ConnectionInterface connection = _lowLevelCommunication.getConnectionInterface();
		if(connection == null) {
			return -1;
		}
		return connection.getSubAdressNumber();
	}

	@Override
	public void continueAuthentication() {
		synchronized(_sync) {
			_waitForConfiguration = false;
			_sync.notifyAll();
		}
	}

	@Override
	public void terminate(boolean error, String message) {
		final DataTelegram terminationTelegram;
		if(error) {
			terminationTelegram = new TerminateOrderTelegram(message);
		}
		else {
			terminationTelegram = new ClosingTelegram();
		}
		terminate(error, message, terminationTelegram);
	}

	public final void terminate(boolean error, String message, DataTelegram terminationTelegram) {
		synchronized(_closedLock) {
			if(_closed) return;
			_closed = true;
		}
		setCommunicationState(CommunicationState.Disconnecting, message);
		synchronized(this) {
			String debugMessage = "Verbindung zum Datenverteiler " + getId() + " wird terminiert. Ursache: " + message;
			if(error) {
				_debug.error(debugMessage);
			}
			else {
				_debug.info(debugMessage);
			}
			if(_lowLevelCommunication != null) {
				_lowLevelCommunication.disconnect(error, message, terminationTelegram);
			}
			_transmitterManager.connectionTerminated(this);
		}
		setCommunicationState(error ? CommunicationState.Error : CommunicationState.NotConnected, message);
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
	public void sendTelegram(DataTelegram telegram) {
		if(Transmitter._debugLevel > 5) System.err.println("T_T  -> " + telegram.toShortDebugString());
		_lowLevelCommunication.send(telegram);
	}

	@Override
	public void sendTelegrams(DataTelegram[] telegrams) {
		_lowLevelCommunication.send(telegrams);
	}

	@Override
	public void update(DataTelegram telegram) {
		if(Transmitter._debugLevel > 5) {
			System.err.println("T_T <-  " + (telegram == null ? "null" : telegram.toShortDebugString()));
		}
		if(telegram == null) {
			return;
		}
		switch(telegram.getType()) {
			case DataTelegram.TRANSMITTER_PROTOCOL_VERSION_REQUEST_TYPE: {
				TransmitterProtocolVersionRequest protocolVersionRequest = (TransmitterProtocolVersionRequest) telegram;
				_version = getPreferredVersion(protocolVersionRequest.getVersions());
				TransmitterProtocolVersionAnswer protocolVersionAnswer = new TransmitterProtocolVersionAnswer(_version);
				sendTelegram(protocolVersionAnswer);
				break;
			}
			case DataTelegram.TRANSMITTER_PROTOCOL_VERSION_ANSWER_TYPE: {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			}
			case DataTelegram.TRANSMITTER_AUTHENTIFICATION_TEXT_REQUEST_TYPE: {
				needsToBeNotAuthenticated();
				TransmitterAuthentificationTextRequest authentificationTextRequest = (TransmitterAuthentificationTextRequest) telegram;
				if(_waitForConfiguration) {
					synchronized(_sync) {
						try {
							while(_waitForConfiguration) {
								if(_closed) return;
								_sync.wait(1000);
							}
						}
						catch(InterruptedException ex) {
							ex.printStackTrace();
							return;
						}
					}
				}
				final long remoteTransmitterId = authentificationTextRequest.getTransmitterId();
				if(!handleRemoteTransmitterId(remoteTransmitterId)) return;
				String text = _authentificationComponent.getAuthentificationText(Long.toString(_connectedTransmitterId));
				TransmitterAuthentificationTextAnswer authentificationTextAnswer = new TransmitterAuthentificationTextAnswer(text);
				sendTelegram(authentificationTextAnswer);
				break;
			}
			case DataTelegram.TRANSMITTER_AUTHENTIFICATION_TEXT_ANSWER_TYPE: {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			}
			case DataTelegram.TRANSMITTER_AUTHENTIFICATION_REQUEST_TYPE: {
				needsToBeNotAuthenticated();
				
				TransmitterAuthentificationRequest authentificationRequest = (TransmitterAuthentificationRequest) telegram;
				String userName = authentificationRequest.getUserName();
				try {
					_userLogin = _lowLevelConnectionsManager.login(
							userName,
							authentificationRequest.getUserPassword(),
							_authentificationComponent.getAuthentificationText(Long.toString(_connectedTransmitterId)),
							_authentificationComponent.getAuthentificationProcess(),
							""
					);


					// Brute-Force-Bremse
					_transmitterManager.throttleLoginAttempt(_userLogin.isAuthenticated());

					if(completeAuthenticationAndSendAnswer(userName)) return;
				}
				catch(ConfigurationException ex) {
					ex.printStackTrace();
					terminate(
							true, "Fehler während der Authentifizierung eines anderen Datenverteilers beim Zugriff auf die Konfiguration: " + ex.getMessage()
					);
					return;
				}
				break;
			}
			case DataTelegram.TRANSMITTER_AUTHENTIFICATION_ANSWER_TYPE: {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			}
			case DataTelegram.SRP_REQUEST_TYPE:
				needsToBeNotAuthenticated();
				_srpRequest = (SrpRequest) telegram;

				SrpVerifierAndUser srpVerifierAndUser;
				try {
					int passwordIndex = _srpRequest.getPasswordIndex();
					if(passwordIndex != -1){
						terminate(true, "Datenverteilerauthentifizierung mit Einmalpassworten ist nicht vorgesehen");
						return;
					}
					srpVerifierAndUser = _transmitterManager.fetchSrpVerifierAndAuthentication(_srpRequest.getUserName());
				}
				catch(SrpNotSupportedException e) {
					// SRP wird von der Konfiguration nicht unterstützt
					_lowLevelCommunication.send(new SrpAnswer(e.getMessage()));
					return;
				}
				final SrpVerifierData srpVerifierData = srpVerifierAndUser.getVerifier();
				_serverCryptoParams = srpVerifierData.getSrpCryptoParameter();
				_srpServerSession = new SrpServerAuthentication(_serverCryptoParams);
				_pendingSrpUserLogin = srpVerifierAndUser.getUserLogin();
				final BigInteger b = _srpServerSession.step1(_srpRequest.getUserName(), srpVerifierData.getSalt(), srpVerifierData.getVerifier(), !_pendingSrpUserLogin.isAuthenticated());
				final SrpAnswer srpAnswer = new SrpAnswer(b, srpVerifierData.getSalt(), _serverCryptoParams);
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
					_transmitterManager.throttleLoginAttempt(true);

					final SrpValidateAnswer answer = new SrpValidateAnswer(m2);
					_lowLevelCommunication.sendDirect(answer);

					_userLogin = _pendingSrpUserLogin;

					if(isIncomingConnection()) {
						_lowLevelCommunication.enableEncryption(new SrpTelegramEncryption(SrpUtilities.bigIntegerToBytes(_srpServerSession.getSessionKey()), false, _serverCryptoParams));
					}
					else {
						setCommunicationState(CommunicationState.Connected, "");
					}

				}
				catch(InconsistentLoginException| SrpNotSupportedException ignored) {
					// Passwort ist falsch

					// Brute-Force-Bremse
					_transmitterManager.throttleLoginAttempt(false);
					
					// Negative Quittung senden
					final SrpValidateAnswer answer = new SrpValidateAnswer(BigInteger.ZERO);
					_lowLevelCommunication.send(answer);
				}
				finally {
					// Bisherige SRP-Sitzung nicht weiterverwenden, Client muss im Falle einer falschen Passworteingabe einen neuen Request senden
					_srpServerSession = null;
				}
				break;
			case DataTelegram.SRP_ANSWER_TYPE:
			case DataTelegram.SRP_VALDIATE_ANSWER_TYPE:
			case DataTelegram.DISABLE_ENCRYPTION_ANSWER_TYPE:
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			case DataTelegram.DISABLE_ENCRYPTION_REQUEST_TYPE:
				needsToBeAuthenticated();
				if(_properties.getEncryptionPreference().shouldDisable(_lowLevelCommunication.getConnectionInterface().isLoopback())){
					_debug.info("Verschlüsselung der Verbindung wird deaktiviert");
					_lowLevelCommunication.sendDirect(new DisableEncryptionAnswer(true));
					_lowLevelCommunication.disableEncryption();
				}
				else {
					_lowLevelCommunication.send(new DisableEncryptionAnswer(false));
				}
				break;
			case DataTelegram.TRANSMITTER_REQUEST_TYPE:
				needsToBeAuthenticated();
				final TransmitterRequest transmitterRequest = (TransmitterRequest) telegram;
				
				String userName = _srpRequest.getUserName();

				if(!handleRemoteTransmitterId(transmitterRequest.getTransmitterId())) return;

				completeAuthenticationAndSendAnswer(userName);
				break;
			case DataTelegram.TRANSMITTER_COM_PARAMETER_REQUEST_TYPE: {
				TransmitterComParametersRequest comParametersRequest = (TransmitterComParametersRequest) telegram;
				long keepAliveSendTimeOut = comParametersRequest.getKeepAliveSendTimeOut();
				if(keepAliveSendTimeOut < 5000) keepAliveSendTimeOut = 5000;
				long keepAliveReceiveTimeOut = comParametersRequest.getKeepAliveReceiveTimeOut();
				if(keepAliveReceiveTimeOut < 6000) keepAliveReceiveTimeOut = 6000;

				TransmitterComParametersAnswer comParametersAnswer = null;
//				if(keepAliveSendTimeOut < keepAliveReceiveTimeOut) {
//					long tmp = keepAliveSendTimeOut;
//					keepAliveSendTimeOut = keepAliveReceiveTimeOut;
//					keepAliveReceiveTimeOut = tmp;
//				}
				comParametersAnswer = new TransmitterComParametersAnswer(keepAliveSendTimeOut, keepAliveReceiveTimeOut);
				sendTelegram(comParametersAnswer);
				_lowLevelCommunication.updateKeepAliveParameters(keepAliveSendTimeOut, keepAliveReceiveTimeOut);
				completeInitialisation();
				break;
			}
			case DataTelegram.TRANSMITTER_COM_PARAMETER_ANSWER_TYPE: {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			}
			case DataTelegram.TRANSMITTER_TELEGRAM_TIME_REQUEST_TYPE: {
				TransmitterTelegramTimeRequest telegramTimeRequest = (TransmitterTelegramTimeRequest) telegram;
				sendTelegram(new TransmitterTelegramTimeAnswer(telegramTimeRequest.getTelegramRequestTime()));
				break;
			}
			case DataTelegram.TRANSMITTER_TELEGRAM_TIME_ANSWER_TYPE: {
				synchronized(_syncSystemTelegramList) {
					_syncSystemTelegramList.add(telegram);
					_syncSystemTelegramList.notifyAll();
				}
				break;
			}
			case DataTelegram.TRANSMITTER_DATA_SUBSCRIPTION_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterDataSubscription subscription = (TransmitterDataSubscription) telegram;
					_transmitterManager.handleTransmitterSubscription(this, subscription);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_DATA_SUBSCRIPTION_RECEIPT_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterDataSubscriptionReceipt receipt = (TransmitterDataSubscriptionReceipt) telegram;
					_transmitterManager.handleTransmitterSubscriptionReceipt(this, receipt);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_DATA_UNSUBSCRIPTION_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterDataUnsubscription unsubscription = (TransmitterDataUnsubscription) telegram;
					_transmitterManager.handleTransmitterUnsubscription(this, unsubscription);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_BEST_WAY_UPDATE_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterBestWayUpdate transmitterBestWayUpdate = (TransmitterBestWayUpdate) telegram;
					_transmitterManager.updateBestWay(this, transmitterBestWayUpdate);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_LISTS_SUBSCRIPTION_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterListsSubscription transmitterListsSubscription = (TransmitterListsSubscription) telegram;
					_transmitterManager.handleListsSubscription(this, transmitterListsSubscription);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_LISTS_UNSUBSCRIPTION_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterListsUnsubscription transmitterListsUnsubscription = (TransmitterListsUnsubscription) telegram;
					_transmitterManager.handleListsUnsubscription(this, transmitterListsUnsubscription);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_LISTS_DELIVERY_UNSUBSCRIPTION_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterListsDeliveryUnsubscription transmitterListsDeliveryUnsubscription = (TransmitterListsDeliveryUnsubscription) telegram;
					_transmitterManager.handleListsDeliveryUnsubscription(this, transmitterListsDeliveryUnsubscription);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_LISTS_UPDATE_TYPE:
			case DataTelegram.TRANSMITTER_LISTS_UPDATE_2_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterListsUpdate transmitterListsUpdate = (TransmitterListsUpdate) telegram;
					_transmitterManager.handleListsUpdate(transmitterListsUpdate);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TRANSMITTER_DATA_TELEGRAM_TYPE: {
				if(_initComplete) {
					needsToBeAuthenticated();
					TransmitterDataTelegram transmitterDataTelegram = (TransmitterDataTelegram) telegram;
					_transmitterManager.handleDataTelegram(this, transmitterDataTelegram);
				}
				else {
					synchronized(_fastTelegramsList) {
						_fastTelegramsList.add(telegram);
					}
				}
				break;
			}
			case DataTelegram.TERMINATE_ORDER_TYPE: {
				TerminateOrderTelegram terminateOrderTelegram = (TerminateOrderTelegram) telegram;
				terminate(true, "Verbindung wurde vom anderen Datenverteiler terminiert. Ursache: " + terminateOrderTelegram.getCause(), null);
			}
			case DataTelegram.CLOSING_TYPE: {
				terminate(false, "Verbindung wurde vom anderen Datenverteiler geschlossen", null);
				break;
			}
			case DataTelegram.TRANSMITTER_KEEP_ALIVE_TYPE: {
				break;
			}
			default: {
				break;
			}
		}
	}

	private boolean completeAuthenticationAndSendAnswer(final String userName) {
		if(!_userLogin.isAuthenticated()) {
			synchronized(_authentificationSync) {
				_authentificationSync.notifyAll();
			}
			_debug.info("Datenverteiler " + _connectedTransmitterId + " hat vergeblich versucht sich als '" + userName + "' zu authentifizieren");
			TransmitterAuthentificationAnswer authentificationAnswer = new TransmitterAuthentificationAnswer(false, -1);
			sendTelegram(authentificationAnswer);
			return false;
		}
		_lowLevelConnectionsManager.updateTransmitterId(this);

		if(_lowLevelConnectionsManager.isDisabledConnection(_connectedTransmitterId)) {
			// Die Verbindung wurde deaktiviert.
			// Damit der andere Datenverteiler nicht anfängt Daten zu senden wird hier in der Authentifizierung blockiert und dann
			// nach einer Minute die Verbindung terminiert.
			// Dier Verbindung wird nicht sofort terminiert, damit der andere Datenverteiler nicht ständig neue Verbindungen aufbaut.

			String msg = "Eingehende Verbindung vom Datenverteiler " + _connectedTransmitterId
					+ " wird in einer Minute terminiert, weil die Verbindung deaktiviert wurde.";
			setCommunicationState(CommunicationState.Connecting, msg);
			try {
				Thread.sleep(60000);
			}
			catch(InterruptedException ignored) {
			}
			// das ist kein richtiger Fehler, aber trotzdem ein TerminateOrderTelegram-Telegramm statt einem ClosingTelegram senden,
			// damit der Grund auf der Gegenseite ankommt.
			terminate(true, "Verbindung zu diesem Datenverteiler wurde deaktiviert.", new TerminateOrderTelegram("Verbindung zu diesem Datenverteiler wurde deaktiviert."));
			return true;
		}

		_debug.info("Datenverteiler " + _connectedTransmitterId + " hat sich als '" + userName + "' erfolgreich authentifiziert");

		TransmitterAuthentificationAnswer authentificationAnswer = new TransmitterAuthentificationAnswer(
				true, _properties.getDataTransmitterId()
		);
		sendTelegram(authentificationAnswer);
		synchronized(_authentificationSync) {
			_authentificationSync.notifyAll();
		}
		if(_isIncomingConnection) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					try {
						authenticate();
					}
					catch(CommunicationError ex) {
						ex.printStackTrace();
					}
				}
			};
			Thread thread = new Thread(runnable);
			thread.start();
		}
		return false;
	}


	private boolean handleRemoteTransmitterId(final long remoteTransmitterId) {
		_debug.info("Datenverteiler " + remoteTransmitterId + " möchte sich authentifizieren");
		final T_T_HighLevelCommunication transmitterConnection;

		transmitterConnection = _lowLevelConnectionsManager.getTransmitterConnection(remoteTransmitterId);

		if(transmitterConnection != null
				&& transmitterConnection.isIncomingConnection()
				&& !transmitterConnection.isClosed()) {
			_debug.warning(
					"Eingehende Verbindung vom Datenverteiler " + remoteTransmitterId
							+ " wird terminiert, weil noch eine andere Verbindung zu diesem Datenverteiler besteht."
			);
			terminate(
					true, "Verbindung wurde terminiert, weil noch eine andere Verbindung zu diesem Datenverteiler besteht."
			);
			return false;
		}
		_connectedTransmitterId = remoteTransmitterId;
		_lowLevelCommunication.setRemoteName("DAV " + _connectedTransmitterId);
		_weight = _transmitterManager.getWeight(_connectedTransmitterId);
		_userForAuthentication = _transmitterManager.getUserNameForAuthentication(_connectedTransmitterId);
		_credentialsForAuthentication = _transmitterManager.getClientCredentialsForAuthentication(_connectedTransmitterId);
		return true;
	}

	/**
	 * Diese Methode wird von der Verbindungsverwaltung aufgerufen, um die Initialisierung einer Verbindung abzuschließen. Zuerst wird eine Instanz der
	 * Anmeldungsverwaltung für diese Verbindung erzeugt und zur Anmeldeverwaltung hinzugefügt. Danach wird die addWayMethode der Wegverwaltung aufgerufen, um
	 * einen Eintrag für den verbundenen Datenverteiler zu erzeugen. Danach werden die Telegramme bearbeitet, die nicht zum Etablieren dieser Verbindung dienen
	 * und vor Fertigstellung der Initialisierung angekommen sind (Online-Daten, Wegeanmeldungen, Listenanmeldungen usw.).
	 *
	 * @return immer true (aus Kompatibilitätsgründen)
	 */
	public final boolean completeInitialisation() {
		if(!_initComplete) {
			_transmitterManager.addWay(this);

			_initComplete = true;
			synchronized(_fastTelegramsList) {
				int size = _fastTelegramsList.size();
				if(size > 0) {
					for(int i = 0; i < size; ++i) {
						update(_fastTelegramsList.removeFirst());
					}
				}
			}
		}
		return true;
	}

	/**
	 * Gibt die höchste unterstützte Version aus den gegebenen Versionen oder -1, wenn keine von den gegebenen Versionen unterstützt wird, zurück.
	 *
	 * @param versions Feld der Versionen
	 * @return die höchste unterstützte Version oder -1
	 */
	private int getPreferredVersion(int[] versions) {
		if(versions == null) {
			return -1;
		}
		return IntStream.of(versions).filter(_supportedProtocolVersions::contains).max().orElse(-1);
	}


	/**
	 * Erledigt den Authentifizierungsprozess beim Remote-Datenverteiler.
	 *
	 * @throws de.bsvrz.dav.daf.main.CommunicationError, wenn bei der initialen Kommunikation mit dem Datenverteiler Fehler aufgetreten sind
	 */
	private void authenticate() throws CommunicationError {
		setCommunicationState(CommunicationState.Authenticating, "");
		TransmitterAuthentificationAnswer authentificationAnswer;

		try {
			if(_version >= 3) {
				try {
					authentificationAnswer = authenticateSrp();
				}
				catch(SrpNotSupportedException ignored) {
					// SRP wird von der Gegenseite nicht unterstützt
					authentificationAnswer = authenticateHmac();
				}
			}
			else {
				authentificationAnswer = authenticateHmac();
			}
		}
		catch(InconsistentLoginException e) {
			terminate(true, "Die Authentifizierung beim anderen Datenverteiler ist fehlgeschlagen");
			throw new CommunicationError("Die Authentifizierung beim anderen Datenverteiler ist fehlgeschlagen", e);
		}
		
		_connectedTransmitterId = authentificationAnswer.getCommunicationTransmitterId();
		_lowLevelCommunication.setRemoteName("DAV " + _connectedTransmitterId);
		setCommunicationState(CommunicationState.Connected, "");
	}

	/**
	 * Authentifizierung als Client mit SRP
	 * @return Antwort vom Server
	 * @throws SrpNotSupportedException SRP wird von der Gegenseite nicht unterstützt
	 * @throws InconsistentLoginException Falsches Passwort
	 * @throws CommunicationError Kommunikationsfehler
	 */
	private TransmitterAuthentificationAnswer authenticateSrp() throws SrpNotSupportedException, InconsistentLoginException, CommunicationError {
		MyTelegramInterface queue = new MyTelegramInterface();
		SrpClientAuthentication.AuthenticationResult authenticationResult = SrpClientAuthentication.authenticate(_userForAuthentication, -1, _credentialsForAuthentication, queue);

		final SrpCryptoParameter clientCryptoParams = authenticationResult.getCryptoParams();
		if(!isIncomingConnection()) {
			_lowLevelCommunication.enableEncryption(new SrpTelegramEncryption(SrpUtilities.bigIntegerToBytes(authenticationResult.getSessionKey()), true, clientCryptoParams));
			
			if(_properties.getEncryptionPreference().shouldDisable(_lowLevelCommunication.getConnectionInterface().isLoopback())) {
				_lowLevelCommunication.send(new DisableEncryptionRequest());
				DisableEncryptionAnswer answer = (DisableEncryptionAnswer)waitForAnswerTelegram(DataTelegram.DISABLE_ENCRYPTION_ANSWER_TYPE, "Antwort auf Anfrage zur Deaktivierung der Verschlüsselung");
				if(answer.isDisabled()){
					_debug.info("Verschlüsselung der Verbindung wird deaktiviert");
					_lowLevelCommunication.disableEncryption();
				}
			}
		}
		_lowLevelCommunication.send(new TransmitterRequest(_lowLevelConnectionsManager.getTransmitterId()));
		return (TransmitterAuthentificationAnswer) queue.getDataTelegram(DataTelegram.TRANSMITTER_AUTHENTIFICATION_ANSWER_TYPE);
	}

	/**
	 * Authentifizierung als Client mit HMAC
	 * @return Antwort vom Server
	 * @throws InconsistentLoginException Falsches Passwort
	 * @throws CommunicationError Kommunikationsfehler
	 */
	private TransmitterAuthentificationAnswer authenticateHmac() throws CommunicationError, InconsistentLoginException {
		if(!_properties.isHmacAuthenticationAllowed()){
			throw new CommunicationError("Anderer Datenverteiler und/oder Konfiguration sind veraltet und unterstützen die sichere SRP-Authentifizierung nicht, und die alte Authentifizierung ist nicht erlaubt. (Aufrufparameter: -erlaubeHmacAuthentifizierung)");
		}
		final TransmitterAuthentificationAnswer authentificationAnswer;// Authentifikationstext holen
		TransmitterAuthentificationTextRequest authentificationTextRequest = new TransmitterAuthentificationTextRequest(
				_properties.getDataTransmitterId()
		);
		sendTelegram(authentificationTextRequest);
		TransmitterAuthentificationTextAnswer authentificationTextAnswer = (TransmitterAuthentificationTextAnswer) waitForAnswerTelegram(
				DataTelegram.TRANSMITTER_AUTHENTIFICATION_TEXT_ANSWER_TYPE, "Aufforderung zur Authentifizierung"
		);

		if(!_credentialsForAuthentication.hasPassword()){
			throw new CommunicationError("Die Authentifizierung mit einem Login-Token ist nicht möglich, da der Datenverteiler nur die Passwort-basierte Authentifizierung unterstützt.\n");
		}
		
		byte[] encryptedPassword = authentificationTextAnswer.getEncryptedPassword(
				_properties.getAuthentificationProcess(), new String(_credentialsForAuthentication.getPassword())
		);

		// User Authentifizierung
		String authentificationProcessName = _properties.getAuthentificationProcess().getName();
		TransmitterAuthentificationRequest authentificationRequest = new TransmitterAuthentificationRequest(
				authentificationProcessName, _userForAuthentication, encryptedPassword
		);
		sendTelegram(authentificationRequest);

		authentificationAnswer = (TransmitterAuthentificationAnswer) waitForAnswerTelegram(
				DataTelegram.TRANSMITTER_AUTHENTIFICATION_ANSWER_TYPE, "Antwort auf eine Authentifizierungsanfrage"
		);
		if(!authentificationAnswer.isSuccessfullyAuthentified()) {
			throw new InconsistentLoginException("Die Authentifizierung beim anderen Datenverteiler ist fehlgeschlagen");
		}
		return authentificationAnswer;
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
	
	@Override
	public String toString() {
		return "[" + _connectedTransmitterId + "]";
	}

	@Override
	public void subscribeToRemote(RemoteCentralSubscription remoteCentralSubscription) {
//		_remoteSubscriptions.add(remoteCentralSubscription);
		TransmitterSubscriptionType transmitterSubscriptionType = null;
		if(remoteCentralSubscription instanceof RemoteSourceSubscription) {
			// Auf eine Quelle meldet man sich als Empfänger an
			transmitterSubscriptionType = TransmitterSubscriptionType.Receiver;
		}
		else {
			// Auf eine Senke meldet man sich als Sender an
			transmitterSubscriptionType = TransmitterSubscriptionType.Sender;
		}
		TransmitterDataSubscription telegram = new TransmitterDataSubscription(
				remoteCentralSubscription.getBaseSubscriptionInfo(), transmitterSubscriptionType.toByte(), Longs.asArray(
				remoteCentralSubscription.getPotentialDistributors()
		)
		);
		sendTelegram(telegram);
	}


	@Override
	public void unsubscribeToRemote(RemoteCentralSubscription remoteCentralSubscription) {
		TransmitterSubscriptionType transmitterSubscriptionType = null;
		if(remoteCentralSubscription instanceof RemoteSourceSubscription) {
			// Auf eine Quelle meldet man sich als Empfänger an
			transmitterSubscriptionType = TransmitterSubscriptionType.Receiver;
		}
		else {
			// Auf eine Senke meldet man sich als Sender an
			transmitterSubscriptionType = TransmitterSubscriptionType.Sender;
		}
		TransmitterDataUnsubscription telegram = new TransmitterDataUnsubscription(
				remoteCentralSubscription.getBaseSubscriptionInfo(), transmitterSubscriptionType.toByte(), Longs.asArray(
				remoteCentralSubscription.getPotentialDistributors()
		)
		);
		sendTelegram(telegram);
	}

	@Override
	public final void sendData(ApplicationDataTelegram telegram, final boolean toCentralDistributor) {
		TransmitterDataTelegram transmitterDataTelegram = new TransmitterDataTelegram(telegram, toCentralDistributor ? (byte) 0 : (byte) 1);
		sendTelegram(transmitterDataTelegram);
	}

	@Override
	public void sendReceipt(
			final long centralTransmitterId,
			final ConnectionState state,
			final TransmitterSubscriptionType receiver,
			RemoteSubscription remoteReceiverSubscription) {
		final byte statusByte;
		switch(state) {
			case TO_REMOTE_OK:
				statusByte = TransmitterSubscriptionsConstants.POSITIV_RECEIP;
				break;
			case TO_REMOTE_NOT_RESPONSIBLE:
				statusByte = TransmitterSubscriptionsConstants.NEGATIV_RECEIP;
				break;
			case TO_REMOTE_NOT_ALLOWED:
				statusByte = TransmitterSubscriptionsConstants.POSITIV_RECEIP_NO_RIGHT;
				break;
			case TO_REMOTE_MULTIPLE:
				statusByte = TransmitterSubscriptionsConstants.MORE_THAN_ONE_POSITIV_RECEIP;
				break;
			default:
				throw new IllegalArgumentException("Status: " + state);
		}
		TransmitterDataSubscriptionReceipt receipt = new TransmitterDataSubscriptionReceipt(
				remoteReceiverSubscription.getBaseSubscriptionInfo(),
				receiver.toByte(), statusByte, centralTransmitterId,
				Longs.asArray(remoteReceiverSubscription.getPotentialDistributors())
		);
		sendTelegram(receipt);
	}

	/** 
	 * Gibt den Verbindungszustand zurück
	 * @return den Verbindungszustand
	 */
	public CommunicationStateAndMessage getState() {
		return _state;
	}

	private class MyTelegramInterface implements SrpClientAuthentication.TelegramInterface {

		@Override
		public SrpAnswer sendAndReceiveRequest(final SrpRequest telegram) throws CommunicationError {
			_lowLevelCommunication.send(telegram);
			return (SrpAnswer) getDataTelegram(DataTelegram.SRP_ANSWER_TYPE);
		}

		@Override
		public SrpValidateAnswer sendAndReceiveValidateRequest(final SrpValidateRequest telegram) throws CommunicationError {
			_lowLevelCommunication.send(telegram);
			return (SrpValidateAnswer) getDataTelegram(DataTelegram.SRP_VALDIATE_ANSWER_TYPE);
		}

		private DataTelegram getDataTelegram(final byte telegramType) throws CommunicationError {
			final DataTelegram telegram = waitForAnswerTelegram(telegramType, "Antwort auf SRP-Authentifizierung " + telegramType);
			if(telegram == null) {
				throw new CommunicationError("Der Datenverteiler antwortet nicht bei der SRP-Authentifizierung");
			}
			return telegram;
		}
	}
}
