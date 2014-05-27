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
import java.util.stream.Stream;

public interface Result {

	/**
	 * Returns the global information about the etcd cluster at the time the request was serviced.
	 *
	 * @return the result meta-data.
	 */
	EtcdMeta getResponseMeta();

	Action getAction();

	Node getNode();

	Optional<Node> getPreviousNode();

	default Stream<Node> streamAllNodes() {
		final Stream.Builder<Node> builder = Stream.<Node>builder();
		addNode(builder, getNode());
		return builder.build();
	}

	static void addNode(Stream.Builder<Node> builder, Node node) {
		builder.add(node);
		node.getNodes().forEach(child -> addNode(builder, child));
	}

}
