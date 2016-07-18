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

package de.bsvrz.dav.dav.subscriptions;

import java.util.Collection;
import java.util.Set;

/**
 * Interface für eine Anmeldung zu einem anderen Datenverteiler (T_T)
 * @author Kappich Systemberatung
 * @version $Revision: 11461 $
 */
public interface RemoteSubscription extends Subscription {
	@Override
	public TransmitterCommunicationInterface getCommunication();

	Set<Long> getPotentialDistributors();

	void setPotentialDistributors(Collection<Long> value);

	void addPotentialDistributor(long transmitterId);

	void removePotentialDistributor(long transmitterId);
}
