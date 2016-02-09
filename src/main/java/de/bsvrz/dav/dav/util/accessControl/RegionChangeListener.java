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
 * Listener-Interface das f�r die Benachrichtigung �ber �nderungen der Liste der ausgew�hlten Objekte in Regionen benutzt wird. Die ausgew�hlten Objekte in
 * Regionen k�nnen sich z.B. �ndern, wenn ein neues Dynamisches Objekt angelegt wird, eine dynamische Menge sich �ndert, oder die Definition der Region ge�ndert
 * wird
 *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
interface RegionChangeListener {

	/**
	 * Wird aufgerufen, wenn sich eine Region ge�ndert hat
	 *
	 * @param region Region, die sich ge�ndert hat
	 */
	void regionChanged(Region region);
}
