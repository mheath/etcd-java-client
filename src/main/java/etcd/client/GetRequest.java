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

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface GetRequest extends Request<Result> {

	/**
	 * By invoking this method before executing this command, etcd will make sure you are talking to the current
	 * master. Followers in a cluster can be behind the leader in their copy of the keyspace. If your application wants
	 * or needs the most up-to-date version of a key then it should ensure it reads from the current leader.
	 *
	 * @return this {@code GetRequest} instance.
	 */
	GetRequest consistent();

	GetRequest recursive();

	GetRequest sorted();

	GetRequest waitForChange();

	GetRequest waitIndex(long index);

}
