/*
 * Copyright 2007 by Kappich Systemberatung Aachen
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

/**
 * Schnittstelle zum Zugriff auf die Matrix der günstigsten Wege.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public interface BestWayManagerInterface {

	/**
	 * Bestimmt den Datenverteiler über den der günstigste Weg zu einem Zieldatenverteiler aufgebaut werden kann.
	 *
	 * @param destinationDavId Objekt-ID des Zieldatenverteilers.
	 *
	 * @return Objekt-ID des Datenverteilers, über den der günstigste Weg läuft, oder <code>-1</code>, falls keine Verbindung zum Zieldatenverteiler existiert.
	 */
	long getBestWay(long destinationDavId);
}
