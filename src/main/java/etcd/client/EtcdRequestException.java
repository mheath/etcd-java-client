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

import java.util.Optional;
import java.util.OptionalLong;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class EtcdRequestException extends EtcdException {

	private final int errorCode;
	private final Long index;
	private final String cause;

	public EtcdRequestException(String message, int errorCode, Long index, String cause) {
		super(message + (cause == null ? "" : " (" + cause + ")"));
		this.errorCode = errorCode;
		this.index = index;
		this.cause = cause;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public OptionalLong getIndex() {
		return index == null ? OptionalLong.empty() : OptionalLong.of(index);
	}

	public Optional<String> getCauseMessage() {
		return Optional.ofNullable(cause);
	}

}
