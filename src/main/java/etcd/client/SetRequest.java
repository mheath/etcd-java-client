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

import java.time.Duration;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface SetRequest extends Request<Result> {

	SetRequest value(String value);

	SetRequest timeToLive(Duration duration);

	SetRequest directory();

	SetRequest mustExist();

	SetRequest mustNotExist();

	SetRequest previousValue(String value);

	SetRequest previousIndex(long index);

	SetRequest inOrder();

}
