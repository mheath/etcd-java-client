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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class EtcdShutDownDuringWait extends AbstractClientTest {

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
	public void shutdownEtcdDuringWait() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);

		final String key = "/test";
		final String value = "testing";

		final Result send = client.prepareSet(key).value(value).send();
		assertEquals(client.prepareGet(key).send().getNode().getValue().get(), value);

		client.prepareGet(key).waitIndex(send.getResponseMeta().getEtcdIndex()).waitForChange().sendAsync((future) -> {
			try {
				future.get();
			} catch (ExecutionException e) {
				if (e.getCause() instanceof EtcdException) {
					latch.countDown();
				}
			}
		});
		killEtcdServer();
		assertTrue(latch.await(2, TimeUnit.SECONDS), "Did not throw exception when server shut down unexpectedly.");
	}

}
