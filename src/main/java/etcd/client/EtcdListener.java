package etcd.client;

import io.netty.util.concurrent.GenericFutureListener;

public interface EtcdListener extends GenericFutureListener<EtcdFuture> {
}
