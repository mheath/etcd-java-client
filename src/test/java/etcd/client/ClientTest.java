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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ClientTest extends AbstractClientTest {

	private EtcdClient client;

	@BeforeClass
	public void init() {
		client = EtcdClientBuilder.create().addHost("localhost", PORT, true).build();
	}

	@AfterClass
	public void cleanup() {
		client.close();
	}

	@Test
	public void blockingGet() {
		final String key = "/";
		final GetRequest getCommand = client.prepareGet(key);
		assertNotNull(getCommand);
		final Result result = getCommand.send();
		assertNotNull(result);

		assertEquals(result.getAction(), Action.GET);
		final Node node = result.getNode();
		assertNotNull(node);
		assertTrue(node.isDirectory());
		assertNull(node.getKey());
	}

	@Test
	public void blockingSetAndGet() {
		final String key = "/setAndGet";
		final String value = "some value goes here";

		final Result setResult = client.prepareSet(key).value(value).send();
		assertNotNull(setResult);
		assertEquals(setResult.getAction(), Action.SET);
		final Node node = setResult.getNode();
		assertNotNull(node);
		assertEquals(node.getKey(), key);
		assertEquals(node.getValue().get(), value);

		final Result getResult = client.prepareGet(key).send();
		assertNotNull(getResult);
		assertEquals(getResult.getNode().getValue().get(), value);
	}

	@Test
	public void metaData() {
		final Result result = client.prepareGet("/").send();
		final EtcdMeta meta = result.getResponseMeta();
		assertNotNull(meta);
		assertTrue(meta.getEtcdIndex() > 0);
		assertTrue(meta.getRaftIndex() > 0);
		assertTrue(meta.getRaftTerm() > 0);

		final String s = meta.toString();
		assertTrue(s.contains(Long.toString(meta.getEtcdIndex())));
		assertTrue(s.contains(Long.toString(meta.getRaftIndex())));
	}

	@Test
	public void blockingDelete() {
		final String key = "/blockingDelete";
		client.prepareSet(key).value("some value").send();
		final Result deleteResult = client.prepareDelete(key).send();
		assertEquals(deleteResult.getAction(), Action.DELETE);
	}

	@Test
	public void changeValue() {
		final String key = "/changeValue";
		final String value1 = "First value";
		final String value2 = "Second value";

		final SetRequest set = client.prepareSet(key).value(value1);
		set.send();

		final Result result = set.value(value2).send();
		assertTrue(result.getPreviousNode().isPresent());
		assertEquals(result.getPreviousNode().get().getValue().get(), value1);
		assertEquals(result.getNode().getValue().get(), value2);
	}

	@Test
	public void setWithTtl() {
		final Duration timeToLive = Duration.ofSeconds(20);
		final Result result = client.prepareSet("/setwithttl").value("some value").timeToLive(timeToLive).send();
		final Node node = result.getNode();
		assertTrue(node.getTimetoLive().isPresent());
		node.getTimetoLive().ifPresent(ttl -> assertEquals(ttl, timeToLive));
		assertTrue(node.getExpiration().isPresent());
	}

	@Test
	public void createDirectory() {
		final Result result = client.prepareSet("newDirectory").directory().send();
		assertTrue(result.getNode().isDirectory());
	}

	@Test
	public void recursiveGet() {
		final String value = "value";
		client.prepareSet("/l1/l2/l3/l4/l5").value(value).send();

		// Ensure initial get only fetches 1 level deep
		final Result l1Result = client.prepareGet("/l1").send();
		final Node node = l1Result.getNode();
		assertTrue(node.isDirectory());
		final List<? extends Node> childNodes = node.getNodes();
		assertEquals(childNodes.size(), 1);
		assertEquals(childNodes.get(0).getNodes().size(), 0);

		final Result result = client.prepareGet("l1").recursive().send();
		final Node l1 = result.getNode();
		assertTrue(l1.isDirectory());
		assertEquals(l1.getKey(), "/l1");
		assertEquals(l1.getNodes().size(), 1);

		final Node l2 = l1.getNodes().get(0);
		assertTrue(l2.isDirectory());
		assertEquals(l2.getKey(), "/l1/l2");
		assertEquals(l2.getNodes().size(), 1);

		final Node l3 = l2.getNodes().get(0);
		assertTrue(l3.isDirectory());
		assertEquals(l3.getKey(), "/l1/l2/l3");
		assertEquals(l3.getNodes().size(), 1);

		final Node l4 = l3.getNodes().get(0);
		assertTrue(l4.isDirectory());
		assertEquals(l4.getKey(), "/l1/l2/l3/l4");
		assertEquals(l4.getNodes().size(), 1);

		final Node l5 = l4.getNodes().get(0);
		assertFalse(l5.isDirectory());
		assertEquals(l5.getKey(), "/l1/l2/l3/l4/l5");
		assertEquals(l5.getValue().get(), value);
	}

	@Test
	public void setWithNoValue() {
		final Result result = client.prepareSet("/emptyValue").send();
		assertTrue(result.getNode().getValue().isPresent());
		assertTrue(result.getNode().getValue().get().isEmpty());
		assertFalse(result.getNode().isDirectory());
	}

	@Test
	public void inOrderKey() {
		final Result result = client.prepareSet("/inorder").inOrder().send();
		assertEquals(result.getAction(), Action.CREATE);
		assertEquals(result.getNode().getKey(), "/inorder/" + String.format("%020d", result.getNode().getCreatedIndex()));
	}

	@Test
	public void mustExist() {
		final String key = "/mustExist";
		try {
			client.prepareSet(key).mustExist().send();
			fail("Should have thrown an exception.");
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 100);
		}

		client.prepareSet(key).value("dummy").send();

		final Result result = client.prepareSet(key).value("new value").mustExist().send();
		assertEquals(result.getAction(), Action.UPDATE);
		assertEquals(result.getNode().getKey(), key);
	}

	@Test
	public void mustNotExist() {
		final String key = "/mustNotExist";

		final Result result = client.prepareSet(key).value("some value").mustNotExist().send();
		assertEquals(result.getAction(), Action.CREATE);
		assertEquals(result.getNode().getKey(), key);

		try {
			client.prepareSet(key).mustNotExist().send();
			fail("Should have thrown an exception.");
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 105);
		}
	}

	@Test
	public void previousValue() {
		final String key = "/previousValue";
		final String value = "Some value";
		client.prepareSet(key).value(value).send();
		try {
			client.prepareSet(key).value("Dummy value").previousValue("bad value").send();
			fail("Should have thrown an exception.");
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 101);
		}
	}

	@Test
	public void previousIndex() {
		final String key = "/previousIndex";
		final Result createResult = client.prepareSet(key).value("Some value").send();

		try {
			client.prepareSet(key).value("dummy value").previousIndex(8587598743848584l).send();
			fail("Should have thrown an exception");
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 101);
		}

		client.prepareSet(key).value("new value").previousIndex(createResult.getNode().getCreatedIndex());
	}

	@Test(expectedExceptions = EtcdRequestException.class)
	public void delete() {
		final String key = "/test";
		client.prepareSet(key).value("some value").send();

		final Result result = client.prepareGet(key).send();
		assertEquals(result.getAction(), Action.GET);

		final Result deleteResult = client.prepareDelete(key).send();
		assertEquals(deleteResult.getAction(), Action.DELETE);

		client.prepareGet(key).send();
	}

	@Test
	public void deletePreviousValue() {
		final String key = "/deletePreviousValue";
		final String originalValue = "some value goes here";
		client.prepareSet(key).value(originalValue).send();

		try {
			client.prepareDelete(key).previousValue("bad value").send();
			fail("Should have thrown an exception");
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 101);
		}
	}

	@Test
	public void deletePreviousIndex() {
		final String key = "/deletePreviousIndex";
		final Result result = client.prepareSet(key).value("some value").send();

		try {
			client.prepareDelete(key).previousIndex(84584393923932435l).send();
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 101);
		}

		final Result deleteResult = client.prepareDelete(key).previousIndex(result.getNode().getCreatedIndex()).send();
		assertEquals(deleteResult.getAction(), Action.COMPAREANDDELETE);
	}

	@Test
	public void deleteDirectory() {
		final String key = "/deleteDirectory";
		client.prepareSet(key).directory().send();
		try {
			client.prepareDelete(key).send();
			fail("Should have thrown exception");
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 102);
		}
		final Result result = client.prepareDelete(key).directory().send();
		assertEquals(result.getAction(), Action.DELETE);
	}

	@Test
	public void deleteRecursive() {
		final String key = "/deleteRecursive";
		client.prepareSet(key + "/a/b/c/d/e/f").value("some value").send();
		try {
			client.prepareDelete(key).send();
		} catch (EtcdRequestException e) {
			assertEquals(e.getErrorCode(), 102);
		}
		final Result result = client.prepareDelete(key).recursive().send();
		assertEquals(result.getAction(), Action.DELETE);
	}

	@Test
	public void getConsistent() {
		final String key = "/getConsistent";
		final String value = "some value";
		client.prepareSet(key).value(value).send();
		final Result result = client.prepareGet(key).consistent().send();
		assertEquals(result.getAction(), Action.GET);
		assertEquals(result.getNode().getValue().get(), value);
	}

	@Test
	public void asyncGet() throws Throwable {
		final Throwable[] error = new Throwable[]{null};
		final CountDownLatch latch = new CountDownLatch(1);

		client.prepareGet("/").sendAsync(future -> {
			try {
				final Result result = future.get();
				assertNotNull(result);
				final Node node = result.getNode();
				assertNotNull(node);
				assertTrue(node.isDirectory());
			} catch (Throwable t) {
				error[0] = t;
			} finally {
				latch.countDown();
			}
		});
		latch.await(1, TimeUnit.SECONDS);
		if (error[0] != null) {
			throw error[0];
		}
	}

	@Test
	public void asyncSetAndGet() throws Throwable {
		final String key = "/asyncSetandGet";
		final String value = "Java 8 lambdas rule.";

		final Throwable[] error = new Throwable[]{null};
		final CountDownLatch latch = new CountDownLatch(1);

		client.prepareSet(key).value(value).sendAsync(setFuture ->
						client.prepareGet(key).sendAsync(getFuture -> {
							try {
								final Result result = getFuture.get();
								assertNotNull(result);
								final Node node = result.getNode();
								assertNotNull(node);
								assertEquals(node.getValue().get(), value);
							} catch (Throwable t) {
								error[0] = t;
							} finally {
								latch.countDown();
							}
						})
		);
		latch.await(1, TimeUnit.SECONDS);
		if (error[0] != null) {
			throw error[0];
		}
	}

}
