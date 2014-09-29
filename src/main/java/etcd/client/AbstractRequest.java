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

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.DefaultPromise;

import java.util.concurrent.ExecutionException;

abstract class AbstractRequest implements Request {

	private final HttpClient client;

	public AbstractRequest(HttpClient client) {
		this.client = client;
	}

	@Override
	public Result send() {
		try {
			return sendAsync().get();
		} catch (InterruptedException e) {
			throw new EtcdException(e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof EtcdException) {
				throw (EtcdException)e.getCause();
			}
			throw new EtcdException(e.getCause());
		}
	}

	public EtcdFuture sendAsync(EtcdListener... listeners) {
		final EtcdPromise promise = new EtcdPromise();
		promise.addListeners(listeners);
		client.send(buildRequest(), response -> {
			try {
				final Result result = createResult(response.getHttpResponse());
				promise.setSuccess(result);
			} catch (Exception e) {
				final EtcdException ee;
				if (e instanceof EtcdException) {
					ee = (EtcdException) e;
				} else {
					ee = new EtcdException(e);
				}
				promise.setFailure(ee);
			} finally {
				response.getHttpResponse().release();
			}
		});
		return promise;
	}

	protected abstract FullHttpRequest buildRequest();

	protected abstract Result createResult(FullHttpResponse response);

	private class EtcdPromise extends DefaultPromise<Result> implements EtcdFuture {
		private EtcdPromise() {
			super(client.getEventLoopGroup().next());
		}
	}

}
