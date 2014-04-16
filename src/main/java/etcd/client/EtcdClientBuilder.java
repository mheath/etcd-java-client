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
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class EtcdClientBuilder {

	final List<URI> hosts = new ArrayList<>();
	boolean retryOnFailure = true;
	int connectTimeout = 2000;
	EventLoopGroup eventLoopGroup;
	Executor executor = Runnable::run;

	public static EtcdClientBuilder create() {
		return new EtcdClientBuilder();
	}

	public EtcdClientBuilder addHost(String localhost, int port) {
		hosts.add(URI.create(String.format("http://%s:%d/", localhost, port)));
		return this;
	}

	public EtcdClient build() {
		return new DefaultEtcdClient(this);
	}

}
