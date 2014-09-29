package etcd.client;

import io.netty.util.concurrent.GenericFutureListener;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface EtcdListener extends GenericFutureListener<EtcdFuture> {
}
