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

/**
 * Low-Level-Authentifizierung für angemeldete Applikationen undDatenverteiler
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public interface LowLevelAuthenticationInterface {

	/**
	 * Prüft, ob es sich um einen gültigen Benutzer handelt
	 * @return die Benutzerid wenn er berechtigt ist sonst -1
	 */
	UserLogin isValidUser(String userName, byte[] encryptedPassword, String text, AuthentificationProcess authenticationProcess, String userTypePid);

	AuthentificationComponent getAuthenticationComponent();
}
