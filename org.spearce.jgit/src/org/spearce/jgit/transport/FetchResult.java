/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spearce.jgit.transport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.spearce.jgit.lib.Ref;

/**
 * Final status after a successful fetch from a remote repository.
 * 
 * @see Transport#fetch(org.spearce.jgit.lib.ProgressMonitor, Collection)
 */
public class FetchResult {
	private final SortedMap<String, TrackingRefUpdate> updates;

	private final List<FetchHeadRecord> forMerge;

	private Map<String, Ref> advertisedRefs;

	FetchResult() {
		updates = new TreeMap<String, TrackingRefUpdate>();
		forMerge = new ArrayList<FetchHeadRecord>();
		advertisedRefs = Collections.<String, Ref> emptyMap();
	}

	void add(final TrackingRefUpdate u) {
		updates.put(u.getLocalName(), u);
	}

	void add(final FetchHeadRecord r) {
		if (!r.notForMerge)
			forMerge.add(r);
	}

	void setAdvertisedRefs(final Map<String, Ref> ar) {
		advertisedRefs = ar;
	}

	/**
	 * Get the complete list of refs advertised by the remote.
	 * <p>
	 * The returned refs may appear in any order. If the caller needs these to
	 * be sorted, they should be copied into a new array or List and then sorted
	 * by the caller as necessary.
	 * 
	 * @return available/advertised refs. Never null. Not modifiable. The
	 *         collection can be empty if the remote side has no refs (it is an
	 *         empty/newly created repository).
	 */
	public Collection<Ref> getAdvertisedRefs() {
		return advertisedRefs.values();
	}

	/**
	 * Get a single advertised ref by name.
	 * <p>
	 * The name supplied should be valid ref name. To get a peeled value for a
	 * ref (aka <code>refs/tags/v1.0^{}</code>) use the base name (without
	 * the <code>^{}</code> suffix) and look at the peeled object id.
	 * 
	 * @param name
	 *            name of the ref to obtain.
	 * @return the requested ref; null if the remote did not advertise this ref.
	 */
	public final Ref getAdvertisedRef(final String name) {
		return advertisedRefs.get(name);
	}

	/**
	 * Get the status of all local tracking refs that were updated.
	 * 
	 * @return unmodifiable collection of local updates. Never null. Empty if
	 *         there were no local tracking refs updated.
	 */
	public Collection<TrackingRefUpdate> getTrackingRefUpdates() {
		return Collections.unmodifiableCollection(updates.values());
	}

	/**
	 * Get the status for a specific local tracking ref update.
	 * 
	 * @param localName
	 *            name of the local ref (e.g. "refs/remotes/origin/master").
	 * @return status of the local ref; null if this local ref was not touched
	 *         during this fetch.
	 */
	public TrackingRefUpdate getTrackingRefUpdate(final String localName) {
		return updates.get(localName);
	}
}