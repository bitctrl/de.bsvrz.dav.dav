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

import de.bsvrz.dav.daf.main.config.SystemObject;
import de.bsvrz.dav.daf.main.config.SystemObjectType;

import java.util.Collection;

/**
 * Kapselt einen Block wie "AuswahlBereich", "AuswahlObjekte" etc. Entspricht aber nicht direkt den DatenmodellBl�cken, da je nach gesetzten Parametern
 * optimierte Klassen benutzt werden
  *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
public interface ObjectSelectionBlock {

	/**
	 * Pr�ft ob das �bergebene Objekt in diesem Block enthalten ist
	 *
	 * @param object Testobjekt
	 *
	 * @return true wenn enthalten
	 */
	boolean contains(SystemObject object);

	/**
	 * Gibt alle Objekte in dem Block zur�ck. Der Aufruf sollte, falls m�glich, vermieden werden, da der Vorgang je nach Definition sehr lange dauern kann
	 *
     * @param type Liste mit Systemobjekttypen die beachtet werden sollen.
	 * @return Liste mit Systemobjekten
	 */
	Collection<SystemObject> getAllObjects(final Collection<? extends SystemObjectType> type);

	/**
	 * Gibt alle Objekttypen zur�ck, die in diesem Block betrachtet werden, bzw. nach denen gefiltert wird. Alle mit {@link #getAllObjects(java.util.Collection)}
	 * zur�ckgelieferten Objekte sind zwingend von diesen Typen, umgekehrt ist allerdings nicht sichergestellt, dass zu allen hier zur�ckgelieferten Typen auch
	 * Objekte vorhanden sind.
	 *
	 * @return Liste mit allen Typen
	 */
	Collection<SystemObjectType> getAllObjectTypes();

	/**
	 * Erstellt einen Listener, der Objekte �ber das �ndern dieses Blocks benachrichtigt
	 *
	 * @param object Listener
	 */
	void addChangeListener(ObjectCollectionChangeListener object);

	/**
	 * Entfernt einen Listener, der Objekte �ber das �ndern dieses Blocks benachrichtigt
	 *
	 * @param object Listener
	 */
	void removeChangeListener(ObjectCollectionChangeListener object);

	/**
	 * Markiert das Objekt als unbenutzt, sodass angemeldete Listener etc. abgemeldet werden k�nnen
	 */
	void dispose();
}
