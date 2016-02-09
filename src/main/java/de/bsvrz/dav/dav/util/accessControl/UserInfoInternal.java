/*
 * Copyright 2010 by Kappich Systemberatung Aachen
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

package de.bsvrz.dav.dav.util.accessControl;

/**
 * Intern benutze Erweiterung des userInfo-Interface, das einige zus�tzliche Methoden bietet, die aber nicht nach au�en sichtbar sein sollen.
 *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
interface UserInfoInternal extends UserInfo {

	/**
	 * Id, die das Systemobjekt des Benutzers darstellt.
	 *
	 * @return Id, mit der das Systemobjekt des Benutzers angefordert werden kann.
	 */
	long getUserId();

	/** Inkrementiert den Referenzz�hler um eins. */
	void incrementReference();

	/** Dekrementiert den Referenzz�hler um eins. */
	void decrementReference();

	/**
	 * Pr�ft, ob keine Referenzen mehr vorhanden sind, und die Klasse gel�scht werden darf.
	 *
	 * @return <code>true</code>, wenn der Benutzer vom System abgemeldet wurde.
	 */
	boolean canBeSafelyDeleted();

	/**
	 * Sollte aufgerufen werden, wenn das Objekt nicht mehr gebraucht wird. Meldet die Empf�nger f�r die Daten atg.benutzerParameter und je nach Implementierung
	 * eventuell weitere ab.
	 */
	void stopDataListener();
}
