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

import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertTrue;

public class HttpClientTest extends AbstractClientTest {

	@Test
	public void httpClient() throws Exception {
		final ServerList serverList = new ServerList();
		serverList.addServer(URI.create("http://localhost:2001"), true);
		final HttpClient httpClient = new HttpClient(new NioEventLoopGroup(), Runnable::run, serverList, false);
		final CountDownLatch latch = new CountDownLatch(1);
		httpClient.send(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/v2/keys/"), (response) -> {
			final FullHttpResponse httpResponse = response.getHttpResponse();
			final ByteBuf contentBuffer = httpResponse.content();
			System.out.println(contentBuffer.toString(Charset.defaultCharset()));
			latch.countDown();
		});
		assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Failed to get valid response from server.");
	}

}
