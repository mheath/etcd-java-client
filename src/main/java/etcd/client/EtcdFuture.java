package etcd.client;

import io.netty.util.concurrent.Future;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface EtcdFuture extends Future<Result> {
}
