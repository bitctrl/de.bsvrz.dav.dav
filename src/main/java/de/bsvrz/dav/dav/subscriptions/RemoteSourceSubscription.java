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

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.BaseSubscriptionInfo;
import de.bsvrz.dav.dav.main.ConnectionState;
import de.bsvrz.dav.dav.main.SubscriptionsManager;
import de.bsvrz.dav.dav.util.accessControl.UserAction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Anmeldung als Empfänger auf eine Quelle bei einem entfernten Zentraldatenverteiler
 *
 * @author Kappich Systemberatung
 * @version $Revision: 11467 $
 */
public class RemoteSourceSubscription implements RemoteSendingSubscription, RemoteCentralSubscription {

	private final SubscriptionsManager _subscriptionsManager;

	private long _centralDistributor = -1;

	private final TransmitterCommunicationInterface _transmitterCommunication;

	private final BaseSubscriptionInfo _baseSubscriptionInfo;

	private SenderState _senderState = SenderState.UNKNOWN;

	private ConnectionState _connectionState = ConnectionState.TO_REMOTE_WAITING;

	private final Set<Long> _potentialCentralDistributors = new HashSet<Long>();

	public RemoteSourceSubscription(
			final SubscriptionsManager subscriptionsManager,
			final BaseSubscriptionInfo baseSubscriptionInfo,
			final TransmitterCommunicationInterface connectionToRemoteDav) {
		_subscriptionsManager = subscriptionsManager;
		_baseSubscriptionInfo = baseSubscriptionInfo;
		_transmitterCommunication = connectionToRemoteDav;
	}

	@Override
	public final void subscribe() {
		_transmitterCommunication.subscribeToRemote(this);
	}

	@Override
	public void unsubscribe() {
		_transmitterCommunication.unsubscribeToRemote(this);
		setState(SenderState.UNKNOWN, 0);
		setRemoteState(-1, ConnectionState.TO_REMOTE_WAITING);
	}

	@Override
	public Set<Long> getPotentialDistributors() {
		return Collections.unmodifiableSet(_potentialCentralDistributors);
	}

	@Override
	public void setPotentialDistributors(final Collection<Long> value) {
		_potentialCentralDistributors.clear();
		_potentialCentralDistributors.addAll(value);
	}

	@Override
	public void addPotentialDistributor(final long transmitterId) {
		_potentialCentralDistributors.add(transmitterId);
	}

	@Override
	public void removePotentialDistributor(final long transmitterId) {
		_potentialCentralDistributors.remove(transmitterId);
	}

	@Override
	public boolean isSource() {
		return true;
	}

	@Override
	public long getCentralDistributorId() {
		return _centralDistributor;
	}

	@Override
	public boolean isRequestSupported() {
		return false;
	}

	@Override
	public SenderState getState() {
		return _senderState;
	}

	@Override
	public void setState(final SenderState senderState, final long centralTransmitterId) {
		if(senderState == _senderState) return;
		_senderState = senderState;
	}

	@Override
	public BaseSubscriptionInfo getBaseSubscriptionInfo() {
		return _baseSubscriptionInfo;
	}

	@Override
	public boolean isAllowed() {
		return _subscriptionsManager.isActionAllowed(getUserId(), _baseSubscriptionInfo, UserAction.SOURCE);
	}

	@Override
	public long getUserId() {
		if(_transmitterCommunication == null) return -1;
		return _transmitterCommunication.getRemoteUserId();
	}

	@Override
	public long getNodeId() {
		return _centralDistributor;
	}

	@Override
	public TransmitterCommunicationInterface getCommunication() {
		return _transmitterCommunication;
	}

	@Override
	public void setRemoteState(final long mainTransmitterId, final ConnectionState state) {
		_connectionState = state;
		_centralDistributor = mainTransmitterId;
	}

	@Override
	public ConnectionState getConnectionState() {
		return _connectionState;
	}

	@Override
	public String toString() {
		return "Ausgehende Anmeldung (" + _senderState + ", " + _connectionState + ")" +
				" auf " + _subscriptionsManager.subscriptionToString(_baseSubscriptionInfo) +
				" zur Quelle über " + _transmitterCommunication +
		        " (Benutzer=" + _subscriptionsManager.objectToString(getUserId()) + ")";
	}
}
