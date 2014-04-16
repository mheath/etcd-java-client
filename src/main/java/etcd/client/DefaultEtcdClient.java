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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class DefaultEtcdClient implements EtcdClient {

	private final ObjectMapper mapper = new ObjectMapper();

	private final HttpClient client;
	private final EventLoopGroup eventLoopGroup;

	DefaultEtcdClient(EtcdClientBuilder builder) {
		EventLoopGroup eventLoopGroup = builder.eventLoopGroup;
		if (eventLoopGroup == null) {
			this.eventLoopGroup = eventLoopGroup = new NioEventLoopGroup();
		} else {
			this.eventLoopGroup = null;
		}
		client = new HttpClient(eventLoopGroup, builder.executor);
	}

	@Override
	public DeleteRequest delete(String key) {
		return new DeleteRequestImpl(client, key);
	}

	@Override
	public GetRequest get(String key) {
		return new GetRequestImpl(client, key);
	}

	@Override
	public Request<Lock> lock(String key, int timeToLive) {
		throw new UnsupportedOperationException("Using the lock module isn't supported yet.");
	}

	@Override
	public SetRequest set(String key) {
		return new SetRequestImpl(client, key);
	}

	@Override
	public WatchRequest watch(String Key) {
		throw new UnsupportedOperationException("The watch API isn't supported yet.");
	}

	@Override
	public void close() {
		if (eventLoopGroup != null) {
			eventLoopGroup.shutdownGracefully();
		}
	}

	private class GetRequestImpl extends AbstractRequest<Result> implements GetRequest {

		private final String key;

		private boolean consistent = false;
		private boolean recursive = false;
		private boolean sorted = false;
		private boolean wait = false;
		private Long waitIndex = null;

		public GetRequestImpl(HttpClient client, String key) {
			super(client);
			key = validateKey(key);
			this.key = key;
		}

		@Override
		protected HttpRequest buildRequest() {
			final StringBuilder uriBuilder = new StringBuilder();
			uriBuilder.append("/v2/keys").append(key);
			final StringBuilder queryBuilder = new StringBuilder();
			if (consistent) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("consistent=true");
			}
			if (recursive) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("recursive=true");
			}
			if (sorted) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("sorted=true");
			}
			if (wait) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("wait=true");
			}
			if (waitIndex != null) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("waitIndex=").append(waitIndex);
			}
			uriBuilder.append(queryBuilder);
			return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uriBuilder.toString());
		}

		@Override
		protected Result createResult(FullHttpResponse response) {
			if (!response.getStatus().equals(HttpResponseStatus.OK)) {
				throwException(response);
			}
			return marshalResult(response);
		}

		@Override
		public GetRequest consistent() {
			consistent = true;
			return this;
		}

		@Override
		public GetRequest recursive() {
			recursive = true;
			return this;
		}

		@Override
		public GetRequest sorted() {
			sorted = true;
			return this;
		}

		@Override
		public GetRequest waitForChange() {
			wait = true;
			return this;
		}

		@Override
		public GetRequest waitIndex(long index) {
			waitIndex = index;
			return this;
		}
	}

	private void throwException(FullHttpResponse response) {
		try {
			final ErrorBody errorBody = mapper.readValue(new ByteBufInputStream(response.content()), ErrorBody.class);
			final String message = errorBody.message == null ? "Error executing request" : errorBody.message;
			throw new EtcdRequestException(message, errorBody.errorCode, errorBody.index, errorBody.cause);
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private class DeleteRequestImpl extends AbstractRequest<Result> implements DeleteRequest {

		private final String key;
		private String previousValue;
		private Long previousIndex;
		private boolean directory;
		private boolean recursive;

		public DeleteRequestImpl(HttpClient client, String key) {
			super(client);
			this.key = validateKey(key);
		}

		@Override
		protected HttpRequest buildRequest() {
			final StringBuilder uriBuilder = new StringBuilder();
			uriBuilder.append("/v2/keys").append(key);
			final StringBuilder queryBuilder = new StringBuilder();
			if (previousValue != null) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("prevValue=").append(urlEncode(previousValue));
			}
			if (previousIndex != null) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("prevIndex=").append(previousIndex);
			}
			if (directory) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("dir=true");
			}
			if (recursive) {
				appendQueryStringSeparator(queryBuilder);
				queryBuilder.append("recursive=true");
			}
			uriBuilder.append(queryBuilder);
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, uriBuilder.toString());
		}

		@Override
		protected Result createResult(FullHttpResponse response) {
			if (!response.getStatus().equals(HttpResponseStatus.OK)) {
				throwException(response);
			}
			return marshalResult(response);
		}

		@Override
		public DeleteRequest previousValue(String value) {
			previousValue = value;
			return this;
		}

		@Override
		public DeleteRequest previousIndex(long index) {
			previousIndex = index;
			return this;
		}

		@Override
		public DeleteRequest directory() {
			directory = true;
			return this;
		}

		@Override
		public DeleteRequest recursive() {
			recursive = true;
			return this;
		}
	}

	private class SetRequestImpl extends AbstractRequest<Result> implements SetRequest {

		private final String key;

		private boolean directory = false;
		private Duration timeToLive;
		private String value;
		private boolean mustExist;
		private boolean mustNotExist;
		private String previousValue;
		private Long previousIndex;
		private boolean inOrder;

		private SetRequestImpl(HttpClient client, String key) {
			super(client);
			this.key = validateKey(key);
		}

		@Override
		protected HttpRequest buildRequest() {
			final HttpMethod method = inOrder ? HttpMethod.POST : HttpMethod.PUT;
			final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, "/v2/keys" + key);
			final StringBuilder body = new StringBuilder();
			if (value != null) {
				body.append("value=").append(urlEncode(value));
			}
			if (timeToLive != null) {
				appendFieldSeparator(body);
				body.append("ttl=").append(timeToLive.getSeconds());
			}
			if (directory) {
				appendFieldSeparator(body);
				body.append("dir=true");
			}
			if (mustExist && mustNotExist) {
				throw new EtcdException("In what universe does it even makes sense for something to be required to both exist and not exist?");
			}
			if (mustExist) {
				appendFieldSeparator(body);
				body.append("prevExist=true");
			}
			if (mustNotExist) {
				appendFieldSeparator(body);
				body.append("prevExist=false");
			}
			if (previousValue != null) {
				appendFieldSeparator(body);
				body.append("prevValue=").append(urlEncode(previousValue));
			}
			if (previousIndex != null) {
				appendFieldSeparator(body);
				body.append("prevIndex=").append(previousIndex);
			}
			final byte[] content = body.toString().getBytes();
			request.headers().add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED + ";charset=utf-8");
			request.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.length);
			request.content().writeBytes(content);
			return request;
		}

		@Override
		protected Result createResult(FullHttpResponse response) {
			if (!(response.getStatus().equals(HttpResponseStatus.CREATED) || response.getStatus().equals(HttpResponseStatus.OK))) {
				throwException(response);
			}
			return marshalResult(response);
		}

		@Override
		public SetRequest value(String value) {
			this.value = value;
			return this;
		}

		@Override
		public SetRequest timeToLive(Duration duration) {
			this.timeToLive = duration;
			return this;
		}

		@Override
		public SetRequest directory() {
			directory = true;
			return this;
		}

		@Override
		public SetRequest mustExist() {
			mustExist = true;
			return this;
		}

		@Override
		public SetRequest mustNotExist() {
			mustNotExist = true;
			return this;
		}

		@Override
		public SetRequest previousValue(String value) {
			this.previousValue = value;
			return this;
		}

		@Override
		public SetRequest previousIndex(long index) {
			this.previousIndex = index;
			return this;
		}

		@Override
		public SetRequest inOrder() {
			inOrder = true;
			return this;
		}
	}

	private void appendQueryStringSeparator(StringBuilder queryString) {
		if (queryString.length() == 0) {
			queryString.append('?');
		} else if (queryString.length() > 0) {
			queryString.append('&');
		}
	}

	private void appendFieldSeparator(StringBuilder body) {
		if (body.length() > 0) {
			body.append('&');
		}
	}

	private static String validateKey(String key) {
		if (!key.startsWith("/")) {
			key = "/" + key;
		}
		return key;
	}

	private String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error(e);
		}
	}

	private Result marshalResult(FullHttpResponse response) {
		try {
			EtcdMeta meta = new EtcdMeta(
					convertLong(response.headers().get("X-Etcd-Index")),
					convertLong(response.headers().get("X-Raft-Index")),
					convertLong(response.headers().get("X-Raft-Term"))
			);

			final ByteBufInputStream inputStream = new ByteBufInputStream(response.content());
			final JsonResult json = mapper.readValue(inputStream, JsonResult.class);

			return new Result() {
				@Override
				public EtcdMeta getResponseMeta() {
					return meta;
				}

				@Override
				public Action getAction() {
					return json.action;
				}

				@Override
				public Node getNode() {
					return json.node;
				}

				@Override
				public Optional<Node> getPreviousNode() {
					return Optional.ofNullable(json.previousNode);
				}
			};
		} catch (IOException e) {
			throw new EtcdException(e);
		}
	}

	private static long convertLong(String value) {
		if (value == null) {
			return -1;
		}
		try {
			return Long.valueOf(value);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static class JsonResult {

		private final Action action;
		private final Node node;
		private final Node previousNode;

		@JsonCreator
		private JsonResult(
				@JsonProperty("action") String action,
				@JsonProperty("node")JsonNode node,
				@JsonProperty("prevNode") JsonNode previousNode) {
			this.action = Action.valueOf(action.toUpperCase());
			this.node = node;
			this.previousNode = previousNode;
		}
	}

	private static class JsonNode implements Node {

		private final long createdIndex;
		private final Long modifiedIndex;
		private final String key;
		private final String value;
		private final Instant expiration;
		private final Duration timeToLive;
		private final boolean directory;
		private final List<? extends Node> nodes;

		@JsonCreator
		private JsonNode(
				@JsonProperty("createdIndex") long createdIndex,
				@JsonProperty("modifiedIndex") Long modifiedIndex,
				@JsonProperty("key") String key,
				@JsonProperty("value") String value,
				@JsonProperty("expiration") String expiration,
				@JsonProperty("ttl") Long timeToLive,
				@JsonProperty("dir") boolean directory,
				@JsonProperty("nodes") List<JsonNode> nodes) {
			this.createdIndex = createdIndex;
			this.modifiedIndex = modifiedIndex;
			this.key = key;
			this.value = value;
			this.expiration = expiration == null ? null : parseDate(expiration);
			this.timeToLive = timeToLive == null ? null : Duration.ofSeconds(timeToLive);
			this.directory = directory;
			this.nodes = nodes == null ? Collections.emptyList() : nodes;
		}

		private Instant parseDate(String expiration) {
			final int timeSep = expiration.lastIndexOf('-');
			expiration = expiration.substring(0, timeSep) + 'Z' + expiration.substring(timeSep + 1);
			return Instant.parse(expiration);
		}

		@Override
		public long getCreatedIndex() {
			return createdIndex;
		}

		@Override
		public Optional<Long> getModifiedIndex() {
			return Optional.ofNullable(modifiedIndex);
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public Optional<String> getValue() {
			return Optional.ofNullable(value);
		}

		@Override
		public Optional<Instant> getExpiration() {
			return Optional.ofNullable(expiration);
		}

		@Override
		public Optional<Duration> getTimetoLive() {
			return Optional.ofNullable(timeToLive);
		}

		@Override
		public boolean isDirectory() {
			return directory;
		}

		@Override
		public List<? extends Node> getNodes() {
			return nodes;
		}
	}

	private static class ErrorBody {
		private final int errorCode;
		private final String cause;
		private final String message;
		private final Long index;

		private ErrorBody(
				@JsonProperty("errorCode") int errorCode,
				@JsonProperty("cause") String cause,
				@JsonProperty("message") String message,
				@JsonProperty("index") Long index) {
			this.errorCode = errorCode;
			this.cause = cause;
			this.message = message;
			this.index = index;
		}
	}
}
