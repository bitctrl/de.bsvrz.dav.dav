/*
 * Copyright 2011 by Kappich Systemberatung Aachen
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

import de.bsvrz.dav.daf.communication.lowLevel.AuthentificationProcess;
import de.bsvrz.dav.daf.communication.protocol.UserLogin;
import de.bsvrz.dav.daf.main.ClientDavParameters;
import de.bsvrz.dav.daf.main.authentication.ClientCredentials;
import de.bsvrz.dav.daf.main.impl.CommunicationConstant;
import de.bsvrz.dav.daf.main.impl.ConfigurationManager;

import java.util.Arrays;

/**
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class LowLevelAuthentication implements LowLevelAuthenticationInterface {

	private final ServerDavParameters _serverDavParameters;

	private final ClientDavParameters _clientDavParameters;

	private final long _transmitterId;

	private SelfClientDavConnection _selfClientDavConnection;

	private AuthentificationComponent _authenticationComponent;


	public LowLevelAuthentication(
			final ServerDavParameters serverDavParameters,
			final ClientDavParameters clientDavParameters,
			final long transmitterId,
			final AuthentificationComponent authenticationComponent) {
		_serverDavParameters = serverDavParameters;
		_clientDavParameters = clientDavParameters;
		_transmitterId = transmitterId;
		_authenticationComponent = authenticationComponent;
		_selfClientDavConnection = null;
	}

	/**
	 * Alte Authentifizierung über Hmac
	 */
	@Override
	public UserLogin isValidUser(
			final String userName,
			final byte[] encryptedPassword,
			final String text,
			final AuthentificationProcess authenticationProcess,
			final String userTypePid) {
		// Spezialbehandlung Konfiguration, Parametrierung und SelfClientDavConnection (falls mit lokaler Konfiguration verbunden)
		if(CommunicationConstant.CONFIGURATION_TYPE_PID.equals(userTypePid)) {
			if(userName.equals(_serverDavParameters.getConfigurationUserName())) {
				ClientCredentials clientCredentials = _serverDavParameters.getConfigurationClientCredentials();
				if(!clientCredentials.hasPassword()) return UserLogin.notAuthenticated();
				final String password = new String(clientCredentials.getPassword());
				final byte[] _encriptedPassword = authenticationProcess.encrypt(password, text);
				if(Arrays.equals(encryptedPassword, _encriptedPassword)) {
					return UserLogin.systemUser();
				}
			}
		}
		else if(CommunicationConstant.PARAMETER_TYPE_PID.equals(userTypePid)) {
			if(userName.equals(_serverDavParameters.getParameterUserName())) {
				ClientCredentials clientCredentials = _serverDavParameters.getParameterClientCredentials();
				if(!clientCredentials.hasPassword()) return UserLogin.notAuthenticated();
				final String password = new String(clientCredentials.getPassword());
				final byte[] _encriptedPassword = authenticationProcess.encrypt(password, text);
				if(Arrays.equals(encryptedPassword, _encriptedPassword)) {
					return UserLogin.systemUser();
				}
			}
		}
		else if(_serverDavParameters.isLocalMode()) {
			if(userName.equals(_clientDavParameters.getUserName())) {
				ClientCredentials clientCredentials = _clientDavParameters.getClientCredentials();
				if(!clientCredentials.hasPassword()) return UserLogin.notAuthenticated();
				final String password = new String(clientCredentials.getPassword());
				final byte[] _encriptedPassword = authenticationProcess.encrypt(password, text);
				if(Arrays.equals(encryptedPassword, _encriptedPassword)) {
					return UserLogin.systemUser();
				}
			}
		}
		// Ask the configuration
		if(_selfClientDavConnection != null) {
			final ConfigurationManager configurationManager = _selfClientDavConnection.getDataModel().getConfigurationManager();
			try {
				long userId = configurationManager.isValidUser(
						userName, encryptedPassword, text, authenticationProcess.getName()
				);
				if(userId == -1){
					return UserLogin.notAuthenticated();
				}
				return UserLogin.user(userId);

			}catch(RuntimeException e){
				e.printStackTrace();
				return UserLogin.notAuthenticated();
			}


		}
		return UserLogin.notAuthenticated();
	}

	public void setSelfClientDavConnection(final SelfClientDavConnection selfClientDavConnection) {
		_selfClientDavConnection = selfClientDavConnection;
	}

	@Override
	public AuthentificationComponent getAuthenticationComponent() {
		return _authenticationComponent;
	}
}
