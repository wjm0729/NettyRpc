package com.nettyrpc.client;

import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RPCFuture for async RPC call
 * Created by luxiaoxun on 2016-03-15.
 * @author jiangmin.wu
 */
public class RPCFuture implements Future<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RPCFuture.class);

    private Sync sync;
    private RpcClient rpcClient;
    private RpcRequest request;
    private RpcResponse response;
    private long startTime;

    private long responseTimeThreshold = 5000;

    private List<AsyncRPCCallback> pendingCallbacks = new ArrayList<AsyncRPCCallback>();
    private ReentrantLock lock = new ReentrantLock();

    public RPCFuture(RpcRequest request, RpcClient rpcClient) {
        this.sync = new Sync();
        this.request = request;
        this.rpcClient = rpcClient;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        sync.acquire(-1);
        if (this.response != null) {
        	Throwable error = response.getError();
        	if(error != null) {
        		throw new ExecutionException(error);
        	}
            return this.response.getResult();
        } else {
        	if(isTimeout()) {
        		throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                + ". Request class name: " + this.request.getClassName()
                + ". Request method: " + this.request.getMethodName());
        	}
            return null;
        }
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean success = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (success) {
            if (this.response != null) {
            	Throwable error = response.getError();
            	if(error != null) {
            		throw new ExecutionException(error);
            	}
                return this.response.getResult();
            } else {
                return null;
            }
        } else {
            throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                    + ". Request class name: " + this.request.getClassName()
                    + ". Request method: " + this.request.getMethodName());
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
		return System.currentTimeMillis() - startTime >= RpcRequest.RPC_REQUEST_TIMEOUT;
	}
    
    public String getRequestId() {
    	return request.getRequestId();
    }

    public void done(RpcResponse reponse) {
        this.response = reponse;
        sync.release(Sync.done);
        invokeCallbacks();
        // Threshold
        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime > this.responseTimeThreshold) {
            LOGGER.warn("Service response time is too slow. Request id = " + reponse.getRequestId() + ". Response Time = " + responseTime + "ms");
        }
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

    public RPCFuture addCallback(AsyncRPCCallback callback) {
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
            return getState() == done ? true : false;
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
