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
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Light HTTP client wrapper around Netty.
 */
// TODO Remove bad hosts from selection pool after error, return after timeout
class HttpClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);

	private static AttributeKey<Consumer<Response>> ATTRIBUTE_KEY = AttributeKey.valueOf(HttpClient.class.getName() + "-attribute");
	private static AttributeKey<FullHttpRequest> REQUEST_KEY = AttributeKey.valueOf(HttpClient.class.getName() + "-request");

	private final Bootstrap bootstrap;
	private final Executor executor;

	private final ServerList servers;
	private final boolean autoReconnect;
//	private final List<Channel> channelPool = new ArrayList<>();
//
//	private final Object lock = new Object();

	public HttpClient(EventLoopGroup eventLoopGroup, Executor executor, ServerList servers, boolean autoReconnect) {
		this.executor = executor;
		this.servers = servers;
		this.autoReconnect = autoReconnect;
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

	public void send(FullHttpRequest request, Consumer<Response> completionHandler) {
		// TODO Add support for TLS
		// TODO Add support for TLS client authentication
		send(servers.serverIterator(), request, completionHandler);
	}

	private void send(Iterator<ServerList.Server> serverIterator, FullHttpRequest request, Consumer<Response> completionHandler) {
		final ServerList.Server server = serverIterator.next();
		final URI address = server.getAddress();
		final ChannelFuture connectFuture = bootstrap.connect(address.getHost(), address.getPort());
		final FullHttpRequest requestCopy = request.copy();
		requestCopy.retain();
		connectFuture.channel().attr(REQUEST_KEY).set(requestCopy);
		connectFuture.channel().attr(ATTRIBUTE_KEY).set(completionHandler);
		connectFuture.addListener((future) -> {
			if (future.isSuccess()) {
				connectFuture.channel().writeAndFlush(request);
			} else {
				server.connectionFailed();
				if (autoReconnect && serverIterator.hasNext()) {
					send(serverIterator, request, completionHandler);
				} else {
					invokeCompletionHandler(completionHandler, new Response(null, new EtcdException(future.cause())));
				}
			}
		});
	}

	private void invokeCompletionHandler(Consumer<Response> completionHandler, Response response) {
		executor.execute(() -> completionHandler.accept(response));
	}

	class HttpClientHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			final FullHttpRequest request = ctx.channel().attr(REQUEST_KEY).getAndRemove();
			try {
				final Consumer<Response> completionCallbackHandler = ctx.channel().attr(ATTRIBUTE_KEY).getAndRemove();
				if (completionCallbackHandler == null) {
					throw new IllegalStateException("Received a response with nothing to handle it.");
				}
				final DefaultFullHttpResponse response = (DefaultFullHttpResponse) msg;

				if (response.getStatus().equals(HttpResponseStatus.MOVED_PERMANENTLY) ||
						response.getStatus().equals(HttpResponseStatus.TEMPORARY_REDIRECT)
						) {
					final URI locationUri = URI.create(response.headers().get(HttpHeaders.Names.LOCATION));
					final URI serverUri;
					if (locationUri.isAbsolute()) {
						serverUri = locationUri;
						request.headers().set(HttpHeaders.Names.HOST, serverUri.getHost());
					} else {
						final InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
						serverUri = URI.create("http://" + address.getHostString() + ":" + address.getPort());
					}
					request.setUri(locationUri.getPath() + (locationUri.getQuery() == null ? "" : locationUri.getQuery()));
					final Iterator<ServerList.Server> serverIterator = Collections.singleton(new ServerList.Server(serverUri)).iterator();
					request.retain();
					send(serverIterator, request, completionCallbackHandler);
				} else {
					response.retain();
					invokeCompletionHandler(completionCallbackHandler, new Response(response, null));
				}
			} finally {
				request.release();
			}
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
