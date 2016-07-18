/*
 * Copyright 2010 by Kappich Systemberatung Aachen
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

package de.bsvrz.dav.dav.util.accessControl;

/**
 * Listener-Interface das für die Benachrichtigung über Änderungen der Liste der ausgewählten Objekte in Regionen benutzt wird. Die ausgewählten Objekte in
 * Regionen können sich z.B. ändern, wenn ein neues Dynamisches Objekt angelegt wird, eine dynamische Menge sich ändert, oder die Definition der Region geändert
 * wird
 *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
interface RegionChangeListener {

	/**
	 * Wird aufgerufen, wenn sich eine Region geändert hat
	 *
	 * @param region Region, die sich geändert hat
	 */
	void regionChanged(Region region);
}
