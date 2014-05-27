/*
 *   Copyright (c) 2014 Intellectual Reserve, Inc.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package etcd.client;

public class EtcdMeta {

	private final long etcdIndex;
	private final long raftIndex;
	private final long raftTerm;

	public EtcdMeta(long etcdIndex, long raftIndex, long raftTerm) {
		this.etcdIndex = etcdIndex;
		this.raftIndex = raftIndex;
		this.raftTerm = raftTerm;
	}

	public long getEtcdIndex() {
		return etcdIndex;
	}

	public long getRaftIndex() {
		return raftIndex;
	}

	public long getRaftTerm() {
		return raftTerm;
	}

	@Override
	public String toString() {
		return "EtcdMeta{" +
				"etcdIndex=" + etcdIndex +
				", raftIndex=" + raftIndex +
				", raftTerm=" + raftTerm +
				'}';
	}
}
