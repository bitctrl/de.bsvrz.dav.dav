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

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.BaseSubscriptionInfo;
import de.bsvrz.dav.daf.main.config.*;

/**
 * Interface, das ein BenutzerInfo-Objekt repräsentiert, das nach verschiedenen Berechtigungen gefragt werden kann.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public interface UserInfo {

	/**
	 * Prüft, ob die angegebenen Daten beim Datenverteiler angemeldet werden dürfen.
	 *
	 * @param info  Daten-Anmeldungs-Informationen
	 * @param state <ul><li>state 0: Als Sender</li> <li>1: Als Empfänger</li> <li>2: Als Quelle</li> <li>3: Als Senke</li></ul>
	 *
	 * @return <code>true</code>, wenn die Daten angemeldet werden dürfen, sonst <code>false</code>.
	 */
	@Deprecated
	boolean maySubscribeData(BaseSubscriptionInfo info, byte state);

	/**
	 * Prüft, ob die angegebenen Daten beim Datenverteiler angemeldet werden dürfen.
	 *
	 * @param info  Daten-Anmeldungs-Informationen
	 * @param action Art der Datenanmeldung
	 *
	 * @return <code>true</code>, wenn die Daten angemeldet werden dürfen, sonst <code>false</code>.
	 */
	boolean maySubscribeData(BaseSubscriptionInfo info, UserAction action);

	/**
	 * Prüft, ob die angegebenen Daten beim Datenverteiler angemeldet werden dürfen.
	 *
	 * @param object Objekt, das verwendet wird
	 * @param attributeGroup Attributgruppe der Daten
	 * @param aspect Aspekt der Daten
	 * @param action Art der Datenanmeldung
	 * @return <code>true</code>, wenn die Daten angemeldet werden dürfen, sonst <code>false</code>.
	 */
	boolean maySubscribeData(SystemObject object, AttributeGroup attributeGroup, Aspect aspect, UserAction action);


	/**
	 * Prüft ob ein Objekt mit den angegeben Daten erstellt, verändert oder gelöscht werden darf
	 * @param area Konfigurationsbereich
	 * @param type Typ des Objekts
	 * @return <code>true</code>, wenn das Objekt erstellt werden darf, sonst <code>false</code>.
	 */
	boolean mayCreateModifyRemoveObject(final ConfigurationArea area, final SystemObjectType type);

	/**
	 * Prüft ob eine menge mit den angegebenen Daten verändert werden darf
	 * @param area Konfigurationsbereich
	 * @param type Typ des Objekts
	 * @return <code>true</code>, wenn das Objekt erstellt werden darf, sonst <code>false</code>.
	 */
	boolean mayModifyObjectSet(final ConfigurationArea area, final ObjectSetType type);

}
