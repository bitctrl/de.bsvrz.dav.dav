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

import de.bsvrz.dav.daf.main.ClientDavInterface;
import de.bsvrz.dav.daf.main.Data;
import de.bsvrz.dav.daf.main.config.Aspect;
import de.bsvrz.dav.daf.main.config.AttributeGroup;
import de.bsvrz.dav.daf.main.config.ConfigurationArea;
import de.bsvrz.dav.daf.main.config.ObjectSetType;
import de.bsvrz.dav.daf.main.config.SystemObject;
import de.bsvrz.dav.daf.main.config.SystemObjectType;

import java.util.*;

/**
 * Kapselt eine Rolle aus den Datenmodell in eine Klasse. Erlaubt Abfragen nach Berechtigung zur Anmeldung von Daten und zum Erstellen von Systemobjekten
 *
 * @author Kappich Systemberatung
 * @version $Revision: 0000 $
 */
class Role extends DataLoader {

	private static final String ATG_ROLE = "atg.rollenAktivit�t";

	private static final String ASPECT_PID = "asp.parameterSoll";

	private final AccessControlManager _accessControlManager;

	/** Daten-Anmeldungs-Aktivit�ten */
	private final List<ActivityData> _activitiesData = new ArrayList<ActivityData>();

	/** Objekterstellungs/-modifizierungs/-l�schungs-Aktivi�ten */
	private final List<ActivityObject> _activitiesObject = new ArrayList<ActivityObject>();

	/** ObjektMengen-Ver�nderungs-Aktivit�ten */
	private final List<ActivityObjectSet> _activitiesObjectSet = new ArrayList<ActivityObjectSet>();

	/** Rollen von denen die Berechtigungen geerbt werden */
	private final List<Role> _innerRoles = new ArrayList<Role>();

	/** Rekursiv referenzierte Rollen, die deaktiviert wurden */
	private final List<Role> _disabledInnerRoles = new ArrayList<Role>();

	/** Bestimmt ob Kind-Rollen additiv vereinigt werden sollen. */
	private boolean _additiveChildren = true;

	/**
	 * Erstellt eine neue Rolle
	 *
	 * @param systemObject         Systemobjekt, das die Daten dieser Rolle enth�lt
	 * @param connection           Verbindung zum Datenverteiler
	 * @param accessControlManager Klasse, die Berechtigungsobjekte verwaltet
	 */
	protected Role(
			final SystemObject systemObject, final ClientDavInterface connection, final AccessControlManager accessControlManager) {
		super(connection, ATG_ROLE, ASPECT_PID, accessControlManager.getUpdateLock());
		_accessControlManager = accessControlManager;
		startDataListener(systemObject);
	}

	/**
	 * Pr�ft den Berechtigungsstatus f�r eine angegebene Datenanmeldung
	 *
	 * @param atg    Attributgruppe
	 * @param asp    Aspekt
	 * @param action Art der Datenanmeldung
	 *
	 * @return <ul> <li>{@link PermissionState#IMPLICIT_FORBIDDEN} wenn keine Aussage gemacht werden kann</li> <li>{@link PermissionState#EXPLICIT_ALLOWED} wenn
	 *         die Aktion von dieser Rolle erlaubt wird</li> <li>{@link PermissionState#EXPLICIT_FORBIDDEN} wenn die Aktion von dieser Rolle explizit verboten
	 *         wird</li> </ul>
	 */
	public PermissionState getPermission(final AttributeGroup atg, final Aspect asp, final UserAction action) {
		if(!isInitialized()) waitForInitialization();
		_readLock.lock();
		try{
			// Verhalten dieser Funktion:
			// Standardm��ig wird keine Aussage angenommen.
			// in Aktivit�tDaten werden dann die Aktivit�ten �berlagert nach der Reihenfolge Keine Aussage < Erlaubt < Verboten
			// Falls in Aktivit�tDaten keine Aussage gemacht werden kann, werden die untergeordneten Rollen beachtet und Additiv vereinigt.

			Role.PermissionState currentState = Role.PermissionState.IMPLICIT_FORBIDDEN;
			for(final ActivityData activity : _activitiesData) {
				final Role.PermissionState p = activity.getPermission(atg, asp, action);
				if(p.getPriority() > currentState.getPriority()) {
					currentState = p;
				}
				if(currentState == Role.PermissionState.EXPLICIT_FORBIDDEN) return Role.PermissionState.EXPLICIT_FORBIDDEN;
			}
			if(_additiveChildren){
				if(currentState != PermissionState.IMPLICIT_FORBIDDEN) return currentState;
				for(final Role innerRole : _innerRoles) {
					final Role.PermissionState p = innerRole.getPermission(atg, asp, action);
					if(p.getPriority() > currentState.getPriority() && p != Role.PermissionState.EXPLICIT_FORBIDDEN) {
						// Falls die innere Rolle Role.PermissionState.EXPLICIT_FORBIDDEN zur�ckgibt muss das ignoriert werden,
						// da sich verschachtelte Rollen nur Additiv erg�nzen sollen
						currentState = p;
					}
				}
			}
			else
			{
				for(final Role innerRole : _innerRoles) {
					final Role.PermissionState p = innerRole.getPermission(atg, asp, action);
					if(p.getPriority() > currentState.getPriority()) {
						currentState = p;
					}
					if(currentState == Role.PermissionState.EXPLICIT_FORBIDDEN) return Role.PermissionState.EXPLICIT_FORBIDDEN;
				}
			}
			return currentState;
		}
		finally {
			_readLock.unlock();
		}
	}

	/**
	 * Pr�ft den Berechtigungsstatus f�r die Erstellung/Ver�nderung/L�schung von Objekten
	 *
	 * @param area Konfigurationsbereich
	 * @param type Objekttyp
	 *
	 * @return <ul> <li>{@link PermissionState#IMPLICIT_FORBIDDEN} wenn keine Aussage gemacht werden kann</li> <li>{@link PermissionState#EXPLICIT_ALLOWED} wenn
	 *         die Aktion von dieser Rolle erlaubt wird</li> <li>{@link PermissionState#EXPLICIT_FORBIDDEN} wenn die Aktion von dieser Rolle explizit verboten
	 *         wird</li> </ul>
	 */
	public PermissionState getPermissionObjectChange(final ConfigurationArea area, final SystemObjectType type) {
		if(!isInitialized()) waitForInitialization();
		_readLock.lock();
		try{
			// Verhalten dieser Funktion:
			// Standardm��ig wird keine Aussage angenommen.
			// in Aktivit�tObjekte werden dann die Aktivit�ten �berlagert nach der Reihenfolge Keine Aussage < Erlaubt < Verboten
			// Falls in Aktivit�tObjekte keine Aussage gemacht werden kann, werden die untergeordneten Rollen beachtet und Additiv vereinigt.

			Role.PermissionState currentState = Role.PermissionState.IMPLICIT_FORBIDDEN;
			for(final ActivityObject activity : _activitiesObject) {
				final Role.PermissionState p = activity.getPermission(area, type);
				if(p.getPriority() > currentState.getPriority()) {
					currentState = p;
				}
				if(currentState == Role.PermissionState.EXPLICIT_FORBIDDEN) return Role.PermissionState.EXPLICIT_FORBIDDEN;
			}
			if(_additiveChildren){
				if(currentState != PermissionState.IMPLICIT_FORBIDDEN) return currentState;
				for(final Role innerRole : _innerRoles) {
					final Role.PermissionState p = innerRole.getPermissionObjectChange(area, type);
					if(p.getPriority() > currentState.getPriority() && p != Role.PermissionState.EXPLICIT_FORBIDDEN) {
						// Falls die innere Rolle Role.PermissionState.EXPLICIT_FORBIDDEN zur�ckgibt muss das ignoriert werden,
						// da sich verschachtelte Rollen nur Additiv erg�nzen sollen
						currentState = p;
					}
				}
			}
			else
			{
				for(final Role innerRole : _innerRoles) {
					final Role.PermissionState p = innerRole.getPermissionObjectChange(area, type);
					if(p.getPriority() > currentState.getPriority()) {
						currentState = p;
					}
					if(currentState == Role.PermissionState.EXPLICIT_FORBIDDEN) return Role.PermissionState.EXPLICIT_FORBIDDEN;
				}
			}
			return currentState;
		}
		finally {
			_readLock.unlock();
		}
	}

	/**
	 * Pr�ft den Berechtigungsstatus f�r die Ver�nderung von Mengen
	 *
	 * @param area Konfigurationsbereich
	 * @param type Mengentyp
	 *
	 * @return <ul> <li>{@link PermissionState#IMPLICIT_FORBIDDEN} wenn keine Aussage gemacht werden kann</li> <li>{@link PermissionState#EXPLICIT_ALLOWED} wenn
	 *         die Aktion von dieser Rolle erlaubt wird</li> <li>{@link PermissionState#EXPLICIT_FORBIDDEN} wenn die Aktion von dieser Rolle explizit verboten
	 *         wird</li> </ul>
	 */
	public PermissionState getPermissionObjectSetChange(final ConfigurationArea area, final ObjectSetType type) {
		if(!isInitialized()) waitForInitialization();
		_readLock.lock();
				try{
			// Verhalten dieser Funktion:
			// Standardm��ig wird keine Aussage angenommen.
			// in Aktivit�tMengen werden dann die Aktivit�ten �berlagert nach der Reihenfolge Keine Aussage < Erlaubt < Verboten
			// Falls in Aktivit�tMengen keine Aussage gemacht werden kann, werden die untergeordneten Rollen beachtet und Additiv vereinigt.

			Role.PermissionState currentState = Role.PermissionState.IMPLICIT_FORBIDDEN;
			for(final ActivityObjectSet activity : _activitiesObjectSet) {
				final Role.PermissionState p = activity.getPermission(area, type);
				if(p.getPriority() > currentState.getPriority()) {
					currentState = p;
				}
				if(currentState == Role.PermissionState.EXPLICIT_FORBIDDEN) return Role.PermissionState.EXPLICIT_FORBIDDEN;
			}
					if(_additiveChildren){
						if(currentState != PermissionState.IMPLICIT_FORBIDDEN) return currentState;
						for(final Role innerRole : _innerRoles) {
							final Role.PermissionState p = innerRole.getPermissionObjectSetChange(area, type);
							if(p.getPriority() > currentState.getPriority() && p != Role.PermissionState.EXPLICIT_FORBIDDEN) {
								// Falls die innere Rolle Role.PermissionState.EXPLICIT_FORBIDDEN zur�ckgibt muss das ignoriert werden,
								// da sich verschachtelte Rollen nur Additiv erg�nzen sollen
								currentState = p;
							}
						}
					}
					else
					{
						for(final Role innerRole : _innerRoles) {
							final Role.PermissionState p = innerRole.getPermissionObjectSetChange(area, type);
							if(p.getPriority() > currentState.getPriority()) {
								currentState = p;
							}
							if(currentState == Role.PermissionState.EXPLICIT_FORBIDDEN) return Role.PermissionState.EXPLICIT_FORBIDDEN;
						}
					}
			return currentState;
		}
		finally {
			_readLock.unlock();
		}
	}

	@Override
	protected void update(final Data data) {
		_writeLock.lock();
		try{
			updateChildren();
			_activitiesData.clear();
			_activitiesObject.clear();
			_activitiesObjectSet.clear();
			_innerRoles.clear();
			_disabledInnerRoles.clear();
			if(data != null) {
				final Data.Array activitiesData = data.getArray("Aktivit�tDaten");
				for(int i = 0; i < activitiesData.getLength(); i++) {
					_activitiesData.add(new ActivityData(activitiesData.getItem(i)));
				}
				final Data.Array activitiesObject = data.getArray("Aktivit�tObjekte");
				for(int i = 0; i < activitiesObject.getLength(); i++) {
					_activitiesObject.add(new ActivityObject(activitiesObject.getItem(i)));
				}
				final Data.Array activitiesObjectSet = data.getArray("Aktivit�tMengen");
				for(int i = 0; i < activitiesObjectSet.getLength(); i++) {
					_activitiesObjectSet.add(new ActivityObjectSet(activitiesObjectSet.getItem(i)));
				}
				final SystemObject[] subRoles = data.getReferenceArray("Rolle").getSystemObjectArray();
				for(final SystemObject subRole : subRoles) {
					_innerRoles.add(_accessControlManager.getRole(subRole));
				}
			}
			_accessControlManager.objectChanged(this);
		}
		finally {
			_writeLock.unlock();
		}
	}

	/** Signalisiert allen deaktivierten referenzierten Rollen, dass diese Rolle ge�ndert wurde. Wird gebraucht um unendliche Rekursionen aufzul�sen. */
	private void updateChildren() {
		_writeLock.lock();
		try{
			final List<Role> tmpArray = new ArrayList<Role>(_disabledInnerRoles);
			_disabledInnerRoles.clear();
			for(final Role disabledInnerRole : tmpArray) {
				disabledInnerRole.reactivateInvalidChildren();
			}
		}
		finally {
			_writeLock.unlock();
		}
	}

	/**
	 * Gibt die referenzierten Unter-Rollen zur�ck
	 *
	 * @return Liste mit Role-Objekten
	 */
	@Override
	protected List<DataLoader> getChildObjects() {
		_readLock.lock();
		try{
			return new ArrayList<DataLoader>(_innerRoles);
		}
		finally {
			_readLock.unlock();
		}
	}

	/**
	 * Deaktiviert die angegebene Unter-Rolle um Rekursionen aufzul�sen
	 *
	 * @param node Das zu entfernende Kindobjekt
	 */
	@Override
	public void deactivateInvalidChild(final DataLoader node) {
		_writeLock.lock();
		try{
			_innerRoles.remove(node);
			_disabledInnerRoles.add((Role)node);
		}
		finally {
			_writeLock.unlock();
		}
	}

	/** Aktiviert alle mit {@link #deactivateInvalidChild(DataLoader)} deaktivierten Elemente wieder. */
	void reactivateInvalidChildren() {
		_writeLock.lock();
		try{
			_innerRoles.addAll(_disabledInnerRoles);
			updateChildren();
		}
		finally {
			_writeLock.unlock();
		}
	}

	/**
	 * Pr�ft, ob sich ein Typ in einer angegebenen Liste von Typen befindet. Dabei kann Typ auch ein abgeleiteter Typ eines erlaubten Typen sein.
	 * @param typeToTest Objekttyp, bei den gepr�ft wird ob er in der Liste enthalten ist
	 * @param typesToAllow Liste von Objekttypen
	 * @return true wenn typeToTest oder einer seiner �bergeordneten Typen in der Liste enthalten ist
	 */
	private static boolean matchesType(final SystemObjectType typeToTest, final List<? extends SystemObjectType> typesToAllow) {
		for(final SystemObjectType typeToAllow : typesToAllow) {
			if(typeToAllow.equals(typeToTest)) {
				return true;
			}
			List<SystemObjectType> list = typeToTest.getSuperTypes();
			while((list != null) && (list.size() > 0)) {
				final ArrayList<SystemObjectType> tmpList = new ArrayList<SystemObjectType>();
				for(final Object aList : list) {
					final SystemObjectType superType = (SystemObjectType)aList;
					if(typeToAllow.equals(superType)) {
						return true;
					}
					final List<SystemObjectType> tmp = superType.getSuperTypes();
					if(tmp != null) {
						tmpList.addAll(tmp);
					}
				}
				list = tmpList;
			}
		}
		return false;
	}

	/** Stellt einen Berechtigungsstatus dar. */
	public enum PermissionState {

		/** Implizites Nein, oder keine Aussage. Geringste Priorit�t */
		IMPLICIT_FORBIDDEN(0),
		/** Explizites Ja, also erlaubt. kann von explizitem Nein �berschrieben werden */
		EXPLICIT_ALLOWED(1),
		/** Explizites Nein, h�chste Priorit�t, �berschreibt alles. */
		EXPLICIT_FORBIDDEN(2);

		private int _priority;

		/**
		 * Erstellt ein neues PermissionState
		 * @param priority H�here Priorit�t hat Vorrang
		 */
		PermissionState(final int priority) {
			_priority = priority;
		}

		/**
		 * Gibt die Priorit�t zur�ck
		 * @return Priorit�t
		 */
		public int getPriority() {
			return _priority;
		}

		/**
		 * Erstellt aus den Werten im Datenmodell ("Ja", "Nein", "KeineAussage") eine PermissionState
		 * @param valueText String
		 * @return PermissionState
		 */
		public static PermissionState parse(final String valueText) {
			if(valueText.equals("Ja")) {
				return PermissionState.EXPLICIT_ALLOWED;
			}
			else if(valueText.equals("Nein")) {
				return PermissionState.EXPLICIT_FORBIDDEN;
			}
			return PermissionState.IMPLICIT_FORBIDDEN;
		}
	}

	/** Kapselt eine Datenanmeldungs-Aktivit�t innerhalb einer Rolle */
	private class ActivityData {

		private List<AttributeGroup> _attributeGroups = new ArrayList<AttributeGroup>();

		private List<ConfigurationArea> _configurationAreas = new ArrayList<ConfigurationArea>();

		private List<Aspect> _aspects = new ArrayList<Aspect>();

		private PermissionState _allowSender;

		private PermissionState _allowReceiver;

		private PermissionState _allowSource;

		private PermissionState _allowDrain;

		/**
		 * Erstellt eine neue Aktivit�t
		 *
		 * @param data Ausschnitt aus einem Rollen-Data-Objekt, das die Aktivit�t enth�lt.
		 */
		public ActivityData(final Data data) {
			final SystemObject[] atgs = data.getReferenceArray("Attributgruppe").getSystemObjectArray();
			for(final SystemObject atg : atgs) {
				_attributeGroups.add((AttributeGroup)atg);
			}
			final SystemObject[] asps = data.getReferenceArray("Aspekt").getSystemObjectArray();
			for(final SystemObject asp : asps) {
				_aspects.add((Aspect)asp);
			}
			final SystemObject[] configurationAreas = data.getReferenceArray("Konfigurationsbereich").getSystemObjectArray();
			for(final SystemObject area : configurationAreas) {
				_configurationAreas.add((ConfigurationArea)area);
			}
			_allowReceiver = PermissionState.parse(data.getTextValue("Empf�nger").getValueText());
			_allowSender = PermissionState.parse(data.getTextValue("Sender").getValueText());
			_allowSource = PermissionState.parse(data.getTextValue("Quelle").getValueText());
			_allowDrain = PermissionState.parse(data.getTextValue("Senke").getValueText());
		}

		/**
		 * Pr�ft den Berechtigungsstatus f�r eine angegebene Datenanmeldung
		 *
		 * @param atg    Attributgruppe
		 * @param asp    Aspekt
		 * @param action Art der Datenanmeldung
		 *
		 * @return <ul> <li>{@link PermissionState#IMPLICIT_FORBIDDEN} wenn keine Aussage gemacht werden kann</li> <li>{@link PermissionState#EXPLICIT_ALLOWED} wenn
		 *         die Aktion von dieser Aktivit�t erlaubt wird</li> <li>{@link PermissionState#EXPLICIT_FORBIDDEN} wenn die Aktion von dieser Aktivit�t explizit
		 *         verboten wird</li> </ul>
		 */
		public PermissionState getPermission(final AttributeGroup atg, final Aspect asp, final UserAction action) {
			if(_attributeGroups.size() != 0 && !_attributeGroups.contains(atg)) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil ATG nicht passt.
			}
			if(_aspects.size() != 0 && !_aspects.contains(asp)) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil ASP nicht passt.
			}
			if(_configurationAreas.size() != 0 && !_configurationAreas.contains(atg.getConfigurationArea())) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil KB nicht zur Attributgruppe passt.
			}
			switch(action) {
				case RECEIVER:
					return _allowReceiver;
				case SENDER:
					return _allowSender;
				case SOURCE:
					return _allowSource;
				case DRAIN:
					return _allowDrain;
				default:
					_debug.error("Unbekannte Aktion: " + action);
					return PermissionState.IMPLICIT_FORBIDDEN;
			}
		}

		@Override
		public String toString() {
			return "ActivityData{" + "_attributeGroups=" + _attributeGroups + ", _aspects=" + _aspects + ", _allowSender=" + _allowSender + ", _allowReceiver="
			       + _allowReceiver + ", _allowSource=" + _allowSource + ", _allowDrain=" + _allowDrain + '}';
		}
	}

	/** Kapselt eine Objekterstellungs/-ver�nderungs-/-entfernungs-Aktivit�t innerhalb einer Rolle */
	private static class ActivityObject {

		private List<SystemObjectType> _systemObjectTypes = new ArrayList<SystemObjectType>();

		private List<ConfigurationArea> _configurationAreas = new ArrayList<ConfigurationArea>();

		private PermissionState _allowChange;

		/**
		 * Erstellt eine neue Aktivit�t
		 *
		 * @param data Ausschnitt aus einem Rollen-Data-Objekt, das die Aktivit�t enth�lt.
		 */
		public ActivityObject(final Data data) {
			final SystemObject[] atgs = data.getReferenceArray("Objekttyp").getSystemObjectArray();
			for(final SystemObject atg : atgs) {
				_systemObjectTypes.add((SystemObjectType)atg);
			}
			final SystemObject[] asps = data.getReferenceArray("Konfigurationsbereich").getSystemObjectArray();
			for(final SystemObject atg : asps) {
				_configurationAreas.add((ConfigurationArea)atg);
			}
			_allowChange = PermissionState.parse(data.getTextValue("ObjekteErzeugen�ndernL�schen").getValueText());
		}

		/**
		 * Pr�ft den Berechtigungsstatus f�r die Erstellung/Ver�nderung/L�schung von Objekten
		 *
		 * @param configurationArea Konfigurationsbereich
		 * @param type              ObjektTyp
		 *
		 * @return <ul> <li>{@link PermissionState#IMPLICIT_FORBIDDEN} wenn keine Aussage gemacht werden kann</li> <li>{@link PermissionState#EXPLICIT_ALLOWED} wenn
		 *         die Aktion von dieser Aktivit�t erlaubt wird</li> <li>{@link PermissionState#EXPLICIT_FORBIDDEN} wenn die Aktion von dieser Aktivit�t explizit
		 *         verboten wird</li> </ul>
		 */
		public PermissionState getPermission(final ConfigurationArea configurationArea, final SystemObjectType type) {
			if(!matchesType(type, _systemObjectTypes )) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil Typ nicht passt.
			}
			if(_configurationAreas.size() != 0 && !_configurationAreas.contains(configurationArea)) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil KB nicht passt.
			}
			return _allowChange;
		}

		@Override
		public String toString() {
			return "ActivityObject{" + "_systemObjectTypes=" + _systemObjectTypes + ", _configurationAreas=" + _configurationAreas + ", _allowChange="
			       + _allowChange + '}';
		}
	}

	private static class ActivityObjectSet {

		private List<ObjectSetType> _objectSetTypes = new ArrayList<ObjectSetType>();

		private List<ConfigurationArea> _configurationAreas = new ArrayList<ConfigurationArea>();

		private PermissionState _allowChange;

		/**
		 * Erstellt eine neue Aktivit�t
		 *
		 * @param data Ausschnitt aus einem Rollen-Data-Objekt, das die Aktivit�t enth�lt.
		 */
		public ActivityObjectSet(final Data data) {
			final SystemObject[] atgs = data.getReferenceArray("Mengentyp").getSystemObjectArray();
			for(final SystemObject atg : atgs) {
				_objectSetTypes.add((ObjectSetType)atg);
			}
			final SystemObject[] asps = data.getReferenceArray("Konfigurationsbereich").getSystemObjectArray();
			for(final SystemObject atg : asps) {
				_configurationAreas.add((ConfigurationArea)atg);
			}
			_allowChange = PermissionState.parse(data.getTextValue("Menge�ndern").getValueText());
		}

		/**
		 * Pr�ft den Berechtigungsstatus f�r die Ver�nderung von Mengen
		 *
		 * @param configurationArea Konfigurationsbereich
		 * @param type              Mengentyp
		 *
		 * @return <ul> <li>{@link PermissionState#IMPLICIT_FORBIDDEN} wenn keine Aussage gemacht werden kann</li> <li>{@link PermissionState#EXPLICIT_ALLOWED} wenn
		 *         die Aktion von dieser Aktivit�t erlaubt wird</li> <li>{@link PermissionState#EXPLICIT_FORBIDDEN} wenn die Aktion von dieser Aktivit�t explizit
		 *         verboten wird</li> </ul>
		 */
		public PermissionState getPermission(final ConfigurationArea configurationArea, final ObjectSetType type) {
			if(_objectSetTypes.size() != 0 && !_objectSetTypes.contains(type)) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil Typ nicht passt.
			}
			if(_configurationAreas.size() != 0 && !_configurationAreas.contains(configurationArea)) {
				return PermissionState.IMPLICIT_FORBIDDEN; // Keine Aussage, weil KB nicht passt.
			}
			return _allowChange;
		}

		@Override
		public String toString() {
			return "ActivityObjectSet{" + "_objectSetTypes=" + _objectSetTypes + ", _configurationAreas=" + _configurationAreas + ", _allowChange="
			       + _allowChange + '}';
		}
	}
}
