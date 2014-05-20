package etcd.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
class ServerList {

	public static final long DEFAULT_FAILURE_BACKOFF_TIME = TimeUnit.SECONDS.toMillis(2);

	private final ArrayList<Server> primaryServers = new ArrayList<>();
	private final ArrayList<Server> secondaryServers = new ArrayList<>();

	private final long failureBackoffTime = DEFAULT_FAILURE_BACKOFF_TIME;

	public ServerList addServer(URI address, boolean primary) {
		final Server server = new Server(address);
		if (primary) {
			primaryServers.add(server);
		} else {
			secondaryServers.add(server);
		}
		return this;
	}

	public Iterator<Server> serverIterator() {
		final Iterator<Server> primaryIterator = populateList(primaryServers).iterator();
		final Iterator<Server> secondaryIterator = populateList(secondaryServers).iterator();
		return new Iterator<Server>() {
			@Override
			public boolean hasNext() {
				return primaryIterator.hasNext() || secondaryIterator.hasNext();
			}

			@Override
			public Server next() {
				return primaryIterator.hasNext() ? primaryIterator.next() : secondaryIterator.next();
			}
		};
	}

	private List<Server> populateList(ArrayList<Server> servers) {
		final long time = System.currentTimeMillis();
		final List<Server> list = servers.stream()
				.filter(server -> server.failTime + failureBackoffTime < time)
				.collect(Collectors.toList());
		Collections.shuffle(list);
		return list;
	}

	static class Server {
		private final URI address;
		private volatile long failTime;

		private Server(URI address) {
			this.address = address;
		}

		public URI getAddress() {
			return address;
		}

		public void connectionFailed() {
			failTime = System.currentTimeMillis();
		}
	}

}
