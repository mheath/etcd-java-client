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

import io.netty.channel.EventLoopGroup;

import java.net.URI;
import java.util.concurrent.Executor;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class EtcdClientBuilder {

	final ServerList servers = new ServerList();
	boolean retryOnConnectFailure = true;
	int connectTimeout = 2000;
	EventLoopGroup eventLoopGroup;
	Executor executor = Runnable::run;

	public static EtcdClientBuilder create() {
		return new EtcdClientBuilder();
	}

	public EtcdClientBuilder addHost(String localhost, int port, boolean primary) {
		final URI uri = URI.create(String.format("http://%s:%d/", localhost, port));
		servers.addServer(uri, primary);
		return this;
	}

	/**
	 * Indicates if the etcd client should attempt to connect to a different node in the etcd cluster if the
	 * first connection attempt failes
	 * @param retryOnConnectFailure {@code true} if the client should attempt to reconnect after a connection
	 *                                          failure, {@code false} otherwise.
	 * @return this build instance
	 */
	public EtcdClientBuilder retryOnConnectFailure(boolean retryOnConnectFailure) {
		this.retryOnConnectFailure = retryOnConnectFailure;
		return this;
	}

	public EtcdClient build() {
		return new DefaultEtcdClient(this);
	}

}
