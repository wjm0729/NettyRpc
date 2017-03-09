package com.nettyrpc.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.nettyrpc.thread.NamedThreadFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * RPC Connect Manage of ZooKeeper
 * Created by luxiaoxun on 2016-03-16.
 * @author jiangmin.wu
 */
public class ConnectManage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectManage.class);

    // 连接维护线程池
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Connection-Checker"));
    private ThreadPoolExecutor connectorThreadPool = null;
    
    // netty work 线程池
    private EventLoopGroup clientEventLoopGroup = null;
    
    private CopyOnWriteArrayList<RpcClientHandler> connectedHandlers = new CopyOnWriteArrayList<>();
    
    // 一个地址可对应多个RpcClientHandler
    private Multimap<SocketAddress, RpcClientHandler> connectedServerNodes;
    
    private ReentrantLock lock = new ReentrantLock();
    private Condition connected = lock.newCondition();
    protected long connectTimeoutMillis = 3000;
    private volatile int connectionPerClient = 1;
    private AtomicInteger roundRobin = new AtomicInteger(0);
    private volatile boolean isRuning = true;

    public ConnectManage() {
    	this(3000, 3);
    }
    
    public ConnectManage(long connectTimeoutMillis, int connectionPerClient) {
    	this(
    			connectTimeoutMillis, 
    			connectionPerClient, 
    			new ThreadPoolExecutor(4, 16, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000), new NamedThreadFactory("ConnectManage-POOL")), 
    			new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2, new NamedThreadFactory("CLIENT-WORK"))
    		);
    }

    public ConnectManage(long connectTimeoutMillis, int connectionPerClient, ThreadPoolExecutor connectorPool, EventLoopGroup clientEventLoopGroup) {
    	this.connectTimeoutMillis = connectTimeoutMillis;
    	this.connectionPerClient = connectionPerClient;
    	this.connectorThreadPool = connectorPool;
    	this.clientEventLoopGroup = clientEventLoopGroup;
    	
    	this.connectedServerNodes = HashMultimap.create();
    	this.connectedServerNodes = Multimaps.synchronizedMultimap(connectedServerNodes);
    	startTimeoutScheduler();
    }
    
    private void startTimeoutScheduler() {
    	scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				try {
					for(RpcClientHandler handler:connectedHandlers) {
						Channel channel = handler.getChannel();
						if(channel.isActive()) {
							handler.cleanTimeoutRequest();
						} else {
							reconnect(handler, handler.getRemotePeer());
						}
					}
				} catch (Exception e) {
					LOGGER.error("", e);
				}
			}
		}, 15, 10, TimeUnit.SECONDS);
	}

    public void updateConnectedServer(List<String> allServerAddress) {
        if (allServerAddress != null) {
            if (allServerAddress.size() > 0) {  // Get available server node
                //update local serverNodes cache
                HashSet<InetSocketAddress> newAllServerNodeSet = new HashSet<InetSocketAddress>();
                for (int i = 0; i < allServerAddress.size(); ++i) {
                    String[] array = allServerAddress.get(i).split(":");
                    if (array.length == 2) { // Should check IP and port
                        String host = array[0];
                        int port = Integer.parseInt(array[1]);
                        final InetSocketAddress remotePeer = new InetSocketAddress(host, port);
                        newAllServerNodeSet.add(remotePeer);
                    }
                }

                // Add new server node
                for (final InetSocketAddress serverNodeAddress : newAllServerNodeSet) {
                    if(connectedServerNodes.get(serverNodeAddress).size() < connectionPerClient) {
                    	for(int i=0; i<connectionPerClient - connectedServerNodes.get(serverNodeAddress).size(); i++) {
                    		connectServerNode(serverNodeAddress);
                    	}
                    }
                }

                // Close and remove invalid server nodes
                for (int i = 0; i < connectedHandlers.size(); ++i) {
                    RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    if (!newAllServerNodeSet.contains(remotePeer)) {
                        LOGGER.info("Remove invalid server node " + remotePeer);
                        for(RpcClientHandler handler : connectedServerNodes.get(remotePeer)) {
                        	 handler.close();
                        }
                        connectedServerNodes.removeAll(remotePeer);
                        connectedHandlers.remove(connectedServerHandler);
                    }
                }

            } else { // No available server node ( All server nodes are down )
                LOGGER.error("No available server node. All server nodes are down !!!");
				for (final RpcClientHandler connectedServerHandler : connectedHandlers) {
					SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
					for (RpcClientHandler handler : connectedServerNodes.get(remotePeer)) {
						handler.close();
					}
					connectedServerNodes.removeAll(connectedServerHandler);
				}
                connectedHandlers.clear();
            }
        }
    }

	public void reconnect(final RpcClientHandler handler, final SocketAddress remotePeer) {
		if (handler != null) {
			connectedHandlers.remove(handler);
			connectedServerNodes.remove(remotePeer, handler);
		}
		connectServerNode((InetSocketAddress) remotePeer);
	}

    private void connectServerNode(final InetSocketAddress remotePeer) {
        connectorThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                Bootstrap b = new Bootstrap();
                b.group(clientEventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcClientInitializer());

                ChannelFuture channelFuture = b.connect(remotePeer);
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            LOGGER.debug("Successfully connect to remote server. remote peer = " + remotePeer);
                            RpcClientHandler handler = channelFuture.channel().pipeline().get(RpcClientHandler.class);
                            addHandler(handler);
                        }
                    }
                });
            }
        });
    }

    private void addHandler(RpcClientHandler handler) {
        InetSocketAddress remoteAddress = (InetSocketAddress) handler.getChannel().remoteAddress();
        if(connectedServerNodes.get(remoteAddress).size() < connectionPerClient) {
        	connectedHandlers.add(handler);
            connectedServerNodes.put(remoteAddress, handler);
            signalAvailableHandler();
            LOGGER.info("new rpc client {} {}", handler.getChannel(), connectedHandlers.size());
        } else {
        	handler.close();
        	LOGGER.info("drop rpc client {} {}", handler.getChannel(), connectedHandlers.size());
        }
    }

    private void signalAvailableHandler() {
        lock.lock();
        try {
            connected.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean waitingForHandler() throws InterruptedException {
        lock.lock();
        try {
            return connected.await(this.connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
	public RpcClientHandler chooseHandler() {
        CopyOnWriteArrayList<RpcClientHandler> handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
        int size = handlers.size();
        while (isRuning && size <= 0) {
            try {
                boolean available = waitingForHandler();
                if (available) {
                    handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
                    size = handlers.size();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Waiting for available node is interrupted! ", e);
                throw new RuntimeException("Can't connect any servers!", e);
            }
        }
        int index = (roundRobin.getAndAdd(1) + size) % size;
        RpcClientHandler handler = handlers.get(index);
        LOGGER.debug("choose rpc handler index {}", index);
        return handler;
    }

    public void stop() {
        isRuning = false;
        for (int i = 0; i < connectedHandlers.size(); ++i) {
            RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
            connectedServerHandler.close();
        }
        signalAvailableHandler();
        scheduler.shutdown();
        connectorThreadPool.shutdown();
        clientEventLoopGroup.shutdownGracefully();
    }

	public int getConnectionPerClient() {
		return connectionPerClient;
	}

	public void setConnectionPerClient(int connectionPerClient) {
		this.connectionPerClient = connectionPerClient;
	}
}
