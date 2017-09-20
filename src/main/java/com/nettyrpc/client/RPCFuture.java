package com.nettyrpc.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;
import com.nettyrpc.protocol.RpcTimeoutException;

import io.netty.channel.Channel;

/**
 * RPCFuture for async RPC call
 * Created by luxiaoxun on 2016-03-15.
 * @author jiangmin.wu
 */
public class RpcFuture implements Future<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcFuture.class);

    private Sync sync;
    private RpcClient rpcClient;
    private RpcClientHandlerWraper handlerWraper;
    
    private RpcRequest request;
    private RpcResponse response;
    private long startTime;
    private volatile byte retryTimes = 0;

    private List<AsyncRPCCallback> pendingCallbacks = new ArrayList<AsyncRPCCallback>();
    private ReentrantLock lock = new ReentrantLock();

    public RpcFuture(RpcRequest request, RpcClient rpcClient, RpcClientHandler handler, SocketAddress socketAddress) {
        this.sync = new Sync();
        this.request = request;
        this.rpcClient = rpcClient;
        this.startTime = System.currentTimeMillis();
        this.handlerWraper = new RpcClientHandlerWraper(socketAddress, handler);
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        // sync.acquire(-1);
        boolean success = sync.tryAcquireNanos(-1, TimeUnit.MILLISECONDS.toNanos(rpcClient.getRequestTimeoutMillis()));
        if (success && this.response != null) {
        	return getResult();
        } else {
        	if(isTimeout()) {
        		if(retryTimes < rpcClient.getRetryTimes()) {
        			LOGGER.info("retry request {}", request);
        			checkHandler();
        			return getHandler().sendRequest(request, rpcClient, getRemotePeer()).get();
        		} else {
            		throw new RpcTimeoutException("Timeout exception. Request id: " + this.request.getRequestId()
                    + " class name: " + this.request.getClassName()
                    + " method: " + this.request.getMethodName()
                    + " params: " + Arrays.toString(this.request.getParameters())
                    );
        		}
        	}
            return null;
        }
    }

	private RpcClientHandler getHandler() {
		return handlerWraper.getHandler();
	}

	private SocketAddress getRemotePeer() {
		return getHandler().getRemotePeer();
	}

	private void checkHandler() {
		SocketAddress remotePeer = handlerWraper.getRemotePeer();
		if(remotePeer != null) {// 固定服务器的
			handlerWraper.setHandler(rpcClient.getConnectManage().chooseHandler(remotePeer));
		} else {// 随机的
			Channel channel = getHandler().getSession().getChannel();
			if(!channel.isActive() || !channel.isWritable()) {
				handlerWraper.setHandler(rpcClient.getConnectManage().chooseHandler());
			}
		}
	}

	private Object getResult() throws ExecutionException {
        Throwable error = response.getError();
        if(error != null) {
            throw new ExecutionException(error);
        }
        // slow log
        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime >= rpcClient.getSlowResponseMillis()) {
            LOGGER.warn("Service response time is too slow. Request id = " + response.getRequestId() + ". Response Time = " + responseTime + "ms");
        }
        return this.response.getResult();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean success = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (success) {
            if (this.response != null) {
            	return getResult();
            } else {
                return null;
            }
        } else {
        	if(retryTimes < rpcClient.getRetryTimes()) {
        		LOGGER.info("retry request {}", request);
        		checkHandler();
    			return getHandler().sendRequest(request, rpcClient, getRemotePeer()).get(timeout, unit);
    		} else {
	            throw new TimeoutException("Timeout exception. Request id: " + this.request.getRequestId()
                + " class name: " + this.request.getClassName()
                + " method: " + this.request.getMethodName()
                + " params: " + Arrays.toString(this.request.getParameters())
                );
    		}
        }
    }

    @Override
    public boolean isCancelled() {
        return sync.isCancelled();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
    	if(isDone()) {
    		return false;
    	}
		if(sync.release(Sync.cancel)) {
			return true;
		}
		return false;
    }
    
    public boolean isTimeout() {
		return System.currentTimeMillis() - startTime >= rpcClient.getRequestTimeoutMillis();
	}

	public long getStartTime() {
		return startTime;
	}
    
    public String getRequestId() {
    	return request.getRequestId();
    }

    public void done(RpcResponse reponse) {
        this.response = reponse;
        sync.release(Sync.done);
        invokeCallbacks();
    }

    private void invokeCallbacks() {
        lock.lock();
        try {
            for (final AsyncRPCCallback callback : pendingCallbacks) {
                runCallback(callback);
            }
        } finally {
            lock.unlock();
        }
    }

    public RpcFuture addCallback(AsyncRPCCallback callback) {
        lock.lock();
        try {
            if (isDone()) {
                runCallback(callback);
            } else {
                this.pendingCallbacks.add(callback);
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    private void runCallback(final AsyncRPCCallback callback) {
        final RpcResponse res = this.response;
        rpcClient.submit(new Runnable() {
            @Override
            public void run() {
                if (!res.isError()) {
                    callback.success(res.getResult());
                } else {
                    callback.fail(new RuntimeException("Response error", new Throwable(res.getError())));
                }
            }
        });
    }

    static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 1L;

        //future status
        private static final int cancel = 2;
        private static final int done = 1;
        private static final int pending = 0;

        protected boolean tryAcquire(int acquires) {
            return (getState() == done ||  getState() == cancel)? true : false;
        }

		protected boolean tryRelease(int state) {
            if (getState() == pending) {
                if (compareAndSetState(pending, state)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isDone() {
            return getState() == done;
        }
        
        public boolean isCancelled() {
        	return getState() == cancel;
		}
    }
}
