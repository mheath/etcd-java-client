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

// TODO Statistics methods
// TODO Add class with set of constants for error codes
public interface EtcdClient extends AutoCloseable {

	DeleteRequest prepareDelete(String key);

	GetRequest prepareGet(String key);

	SetRequest prepareSet(String key);

	WatchRequest watch(String Key);

	@Override
	void close();
}
