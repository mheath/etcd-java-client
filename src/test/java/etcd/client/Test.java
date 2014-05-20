package etcd.client;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class Test {
	public static void main(String[] args) {
		try (final EtcdClient client = new EtcdClientBuilder().addHost("10.118.216.219", 4001, true).build()) {
			final Result result = client.get("/hm/v4/apps/crashes").recursive().send();
			result.streamAllNodes().forEach(System.out::println);
		}
	}
}
