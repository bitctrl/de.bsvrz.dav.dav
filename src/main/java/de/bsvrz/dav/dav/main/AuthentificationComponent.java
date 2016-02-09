/*
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2004 by Kappich+Kni� Systemberatung, Aachen
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

import de.bsvrz.dav.daf.communication.lowLevel.AuthentificationProcess;

import java.util.*;

/**
 * Diese Klasse enth�lt die Komponenten zur Authentifizierung eines Benutzers/Applikation.
 *
 * TBD: Authentification ist kein englisches Wort. Umbenennen in Authentication?
 *
 * @author Kappich Systemberatung
 * @version $Revision: 11481 $
 */
public class AuthentificationComponent {

	/**
	 * Speichert zu dem Namen einer Applikation einen Zufallstext, der mit {@link #getAuthentificationText(String)} erzeugt wurde.
	 * <p/>
	 * Als Schl�ssel dient der Name der Applikation, als Value wird der Zufallstext zur�ckgegeben.
	 */
	private Hashtable<String, String> _table;

	/** Das Authentifikationsverfahren */
	private AuthentificationProcess _authentificationProcess;

	/** @param authentificationProcess Verschl�sslungsverfahren, das benutzt wird um Authentifizierungsdaten zu pr�fen. */
	public AuthentificationComponent(AuthentificationProcess authentificationProcess) {
		_table = new Hashtable<String, String>();
		_authentificationProcess = authentificationProcess;
	}

	/**
	 * Gibt den Authentifikationsprozess zur�ck, der benutzt wird um die Authentifizierung eines Benutzers zu pr�fen.
	 *
	 * @return authentificationProcess der Authentifikationsprozess
	 */
	public final AuthentificationProcess getAuthentificationProcess() {
		return _authentificationProcess;
	}

	/**
	 * Generiert f�r eine Applikation einen Zufallstext und speichert diesen.
	 *
	 * @param applicationName Name der Applikation, f�r den ein Zufallstext erzeugt werden soll.
	 *
	 * @return Zufallstext. Jeder Aufruf der Methode, mit der selben Applikation, gibt den selben Zufallstext zur�ck.
	 */
	public synchronized final String getAuthentificationText(String applicationName) {
		String text = _table.get(applicationName);
		if(text == null) {
			text = "Datenverteiler@" + System.currentTimeMillis() + Math.random();
			_table.put(applicationName, text);
		}
		return text;
	}

	/**
	 * Diese Methode verschl�sselt mit dem Passwort der Appliktion den Zufallstext, der zu einer Applikation geh�rt. Danach wird gepr�ft, ob der �bergebene
	 * verschl�sselte Text <code>encryptedRandomText</code> mit dem gerade erzeugten verschl�sselten Text �bereinstimmt.
	 * <p/>
	 * Ist dies der Fall, so wird <code>true</code> zur�ckgegeben.
	 * <p/>
	 * Die Methode l�scht in jedem Fall bei Beendigung den Zufallstext, der zu einer Applikation geh�rt. Ein zweiter Anmeldeversuch mit dem selben Zufallstext ist
	 * also nicht m�glich und wird immer zum Ergebnis <code>false</code> f�hren, auch wenn das Passwort richtig ist.
	 *
	 * @param applicationName     Name der Applikation, die sich authentifizieren m�chte.
	 * @param password            Das unverschl�sseltes Passwort der Applikation, die sich authentifizieren m�chte.
	 * @param encryptedRandomText Verschl�sselter Zufallstext der Applikation, die sich authentifizieren will. Der Zufallstext wurde mit einem Passwort
	 *                            verschl�sselt.
	 *
	 * @return <code>true</code>, die Authentifizierung war erfolgreich; <code>false</code> sonst
	 *
	 * @see {@link #getAuthentificationText(String)}
	 */
	synchronized final boolean authentify(String applicationName, String password, byte[] encryptedRandomText) {
		boolean success = false;
		if(encryptedRandomText != null) {
			String text = _table.get(applicationName);
			if((text != null) && (_authentificationProcess != null)) {
				byte[] _encriptedPassword = _authentificationProcess.encrypt(password, text);
				if(_encriptedPassword != null) {
					if(Arrays.equals(encryptedRandomText, _encriptedPassword)) {
						success = true;
					}
				}
			}
		}
		_table.remove(applicationName);
		return success;
	}
}

