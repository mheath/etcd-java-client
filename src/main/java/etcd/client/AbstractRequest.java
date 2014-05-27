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

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;

import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractRequest<T> implements Request<T> {

	private final HttpClient client;

	public AbstractRequest(HttpClient client) {
		this.client = client;
	}

	@Override
	public T send() {
		final HttpClient.Response[] clientResponse = new HttpClient.Response[1];
		final Object lock = new Object();
		synchronized (lock) {
			final HttpRequest httpRequest = buildRequest();
			client.send(httpRequest, response -> {
				clientResponse[0] = response;
				synchronized (lock) {
					lock.notify();
				}
			});
			try {
				lock.wait();
			} catch (InterruptedException e) {
				throw new EtcdException(e);
			}
		}
		final DefaultFullHttpResponse response = clientResponse[0].getHttpResponse();
		try {
			return createResult(response);
		} finally {
			response.release();
		}
	}

	@Override
	public void send(Consumer<Supplier<T>> consumer) {
		client.send(buildRequest(), response -> {
			try {
				final T result = createResult(response.getHttpResponse());
				consumer.accept(() -> result);
			} catch (Exception e) {
				final EtcdException ee;
				if (e instanceof EtcdException) {
					ee = (EtcdException) e;
				} else {
					ee = new EtcdException(e);
				}
				consumer.accept(() -> { throw ee; });
			} finally {
				response.getHttpResponse().release();
			}
		});
	}

	protected abstract HttpRequest buildRequest();

	protected abstract T createResult(FullHttpResponse response);

}
