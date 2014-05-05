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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Light HTTP client wrapper around Netty.
 *
 * @author Mike Heath <elcapo@gmail.com>
 */
// TODO Remove bad hosts from selection pool after error, return after timeout
class HttpClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);

	private static AttributeKey<Consumer<Response>> ATTRIBUTE_KEY = AttributeKey.valueOf(HttpClient.class.getName() + "-attribute");

	private final Bootstrap bootstrap;
	private final Executor executor;

	private final List<URI> hosts;
//	private final List<Channel> channelPool = new ArrayList<>();
//
//	private final Object lock = new Object();

	public HttpClient(EventLoopGroup eventLoopGroup, Executor executor, List<URI> hosts) {
		this.executor = executor;
		this.hosts = hosts;
		bootstrap = new Bootstrap()
				.group(eventLoopGroup)
				.channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel channel) throws Exception {
						final ChannelPipeline pipeline = channel.pipeline();

						pipeline.addLast(
								new HttpClientCodec(4096, 8192, 8192, true),
								new HttpObjectAggregator(1024 * 1024),
								new HttpClientHandler());
					}
				})
				.validate();
	}

	public void send(HttpRequest request, Consumer<Response> completionHandler) {
		// TODO Load balance across all the etcd hosts
		// TODO Add support for TLS
		// TODO Add suport for TLS client authentication
		final URI server = hosts.get(0);
		final ChannelFuture connectFuture = bootstrap.connect(server.getHost(), server.getPort());
		connectFuture.channel().attr(ATTRIBUTE_KEY).set(completionHandler);
		connectFuture.addListener((future) -> {
			if (future.isSuccess()) {
				connectFuture.channel().writeAndFlush(request);
			} else {
				// TODO Add support for trying to connect to another node in the cluster
				invokeCompletionHandler(completionHandler, new Response(null, new EtcdException(future.cause())));
			}
		});
	}

	private void invokeCompletionHandler(Consumer<Response> completionHandler, Response response) {
		executor.execute(() -> completionHandler.accept(response));
	}

	class HttpClientHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			final Consumer<Response> completionCallbackHandler = ctx.channel().attr(ATTRIBUTE_KEY).getAndRemove();
			if (completionCallbackHandler == null) {
				throw new IllegalStateException("Received a response with nothing to handle it.");
			}
			invokeCompletionHandler(completionCallbackHandler, new Response((DefaultFullHttpResponse) msg, null));
			ctx.close();
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//			synchronized (lock) {
//				channelPool.remove(ctx.channel());
//			}
			final Consumer<Response> completionCallbackHandler = ctx.channel().attr(ATTRIBUTE_KEY).getAndRemove();
			if (completionCallbackHandler != null) {
				invokeCompletionHandler(completionCallbackHandler, new Response(null, new EtcdException("Connection closed unexpectedly")));
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			final Consumer<Response> completionCallbackHandler = ctx.channel().attr(ATTRIBUTE_KEY).getAndRemove();
			if (completionCallbackHandler != null) {
				final Response response;
				if (cause instanceof EtcdException) {
					response = new Response(null, (EtcdException)cause);
				} else {
					response = new Response(null, new EtcdException(cause));
				}
				invokeCompletionHandler(completionCallbackHandler, response);
			} else {
				LOGGER.error("Error processing server request", cause);
			}
			ctx.channel().close();
		}
	}

	class Response {
		private final DefaultFullHttpResponse response;
		private final EtcdException exception;

		Response(DefaultFullHttpResponse response, EtcdException exception) {
			this.response = response;
			this.exception = exception;
		}

		public DefaultFullHttpResponse getHttpResponse() {
			if (exception != null) {
				throw exception;
			}
			return response;
		}
	}

}
