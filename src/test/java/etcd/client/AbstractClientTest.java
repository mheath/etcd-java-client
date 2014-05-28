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

import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public abstract class AbstractClientTest {

	public static final int PORT = 2001;
	private Process etcdProcess;

	@BeforeSuite
	public void startEtcdServer() throws Exception {
		System.out.println("Starting ETCD");
		String etcd = System.getProperty("etcd");
		if (etcd == null) {
			etcd = "etcd";
		}
		etcdProcess = new ProcessBuilder()
				.command(etcd, "-name", "test0", "-data-dir", "test0", "-addr=127.0.0.1:" + PORT, "-peer-bind-addr=127.0.0.1:2002")
				.inheritIO()
				.start();
		final long start = System.currentTimeMillis();
		while (isPortAvailable(PORT) && etcdProcess.isAlive()) {
			if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(5)) {
				etcdProcess.destroyForcibly();
				Assert.fail("etcd does not appear to have started");
				Thread.sleep(100);
			}
		}
		if (!etcdProcess.isAlive()) {
			Assert.fail("etcd did not start");
		}
	}

	@AfterSuite
	public void killEtcdServer() throws Exception {
		if (etcdProcess != null) {
			System.out.println("Killing ETCD");
			etcdProcess.destroy();
		}
		System.out.println("Deleting etcd droppings");
		Files.walkFileTree(Paths.get("test0"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static boolean isPortAvailable(int port) {
		try {
			new Socket("localhost", port).close();
			return false;
		} catch (IOException e) {
			return true;
		}
	}

}
