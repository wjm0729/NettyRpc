package com.nettyrpc.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.nettyrpc.protocol.RpcTimeoutException;
import com.nettyrpc.thread.NamedThreadFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * RPC Connect Manage of ZooKeeper 
 * 
 * @author luxiaoxun on 2016-03-16.
 * @author jiangmin.wu
 */
public class ConnectManage {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConnectManage.class);

	private RpcClient rpcClient;
	private ConcurrentHashMap<String, RpcFuture> pendingRPC = new ConcurrentHashMap<>();

	// 连接维护线程池
	private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Connection-Checker"));
	
	// 创建连接线程池
	private ThreadPoolExecutor clientThreadPool = null;

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

	private boolean cluster = false;
	private String serviceAddress;
	
	private ServiceDiscovery serviceDiscovery;
	private String zkRegistryPath = "/registry";
	
	public ConnectManage(String serviceAddress) {
		this(serviceAddress, true);
	}

	public ConnectManage(String serviceAddress, boolean cluster) {
		this(serviceAddress, cluster, 
				new ThreadPoolExecutor(1, 2, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000), new NamedThreadFactory("ConnectManage-POOL")),
				new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new NamedThreadFactory("CLIENT-WORK")));
	}

	public ConnectManage(String serviceAddress, boolean cluster, ThreadPoolExecutor connectorPool, EventLoopGroup clientEventLoopGroup) {
		this.cluster = cluster;
		this.serviceAddress = serviceAddress;
		this.clientThreadPool = connectorPool;
		this.clientEventLoopGroup = clientEventLoopGroup;

		this.connectedServerNodes = HashMultimap.create();
		this.connectedServerNodes = Multimaps.synchronizedMultimap(connectedServerNodes);
		startTimeoutScheduler();
	}
	
	public void init() {
		if(isCluster()) {
			serviceDiscovery = new ServiceDiscovery(serviceAddress);
		} else {// 非集群模式, 需开启重连机制
			String[] strArr = serviceAddress.split(":");
			if(strArr.length != 2) {
				throw new RuntimeException("serviceAddress invalid");
			}
			final SocketAddress socketAddress = new InetSocketAddress(strArr[0], Integer.valueOf(strArr[1]));
			scheduler.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					try {
						while (activeHandlers() < connectionPerClient) {
							final CountDownLatch latch = new CountDownLatch(1);
							connect(socketAddress, latch);
							latch.await(connectTimeoutMillis * 2, TimeUnit.MILLISECONDS);
							if(activeHandlers() == 0) {
								TimeUnit.MILLISECONDS.sleep(connectTimeoutMillis);
							}
						}
					} catch (Exception e) {
					}
				}
			}, 0, connectTimeoutMillis, TimeUnit.MILLISECONDS);
		}
	}

	private void startTimeoutScheduler() {
		scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				try {
					checkTimeoutRequest();
				} catch (Exception e) {
					LOGGER.error("", e);
				}
			}
		}, 5, 10, TimeUnit.MILLISECONDS);
	}

	void checkTimeoutRequest() {
		List<RpcFuture> list = null;
		for(RpcFuture f : pendingRPC.values()) {
			if(f.isTimeout()) {
				if(list == null) {
					list = new ArrayList<>();
				}
				list.add(f);
			}
		}

		if(list != null) {
			for(RpcFuture f : list) {
				if(!f.isDone() && f.cancel(false)) {
					pendingRPC.remove(f.getRequestId());
				}
			}
		}
	}

	public void updateConnectedServer(List<String> allServerAddress) {
		if (allServerAddress != null) {
			if (allServerAddress.size() > 0) { // Get available server node
				// update local serverNodes cache
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
					if (connectedServerNodes.get(serverNodeAddress).size() < connectionPerClient) {
						for (int i = 0; i < connectionPerClient - connectedServerNodes.get(serverNodeAddress).size(); i++) {
							connectServerNode(serverNodeAddress, null);
						}
					}
				}

				// Close and remove invalid server nodes
				for (int i = 0; i < connectedHandlers.size(); ++i) {
					RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
					SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
					if (!newAllServerNodeSet.contains(remotePeer)) {
						LOGGER.info("Remove invalid server node " + remotePeer);
						for (RpcClientHandler handler : connectedServerNodes.get(remotePeer)) {
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
					connectedServerNodes.removeAll(remotePeer);
				}
				connectedHandlers.clear();
			}
		}
	}

	public ChannelFuture connectServerNode(final InetSocketAddress remotePeer, final CountDownLatch latch) {
		Future<?> future = clientThreadPool.submit(new Callable<ChannelFuture>() {
			@Override
			public ChannelFuture call() throws Exception {
				return connect(remotePeer, latch);
			}
		});
		return (ChannelFuture) future;
	}
	Bootstrap bootstrap;
	private ChannelFuture connect(final SocketAddress remotePeer, final CountDownLatch latch) {
		synchronized(remotePeer.toString().intern()) {
			LOGGER.info("trying to connect to {}", remotePeer);
			if(bootstrap == null) {
				bootstrap = new Bootstrap().group(clientEventLoopGroup).channel(NioSocketChannel.class)
					.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)connectTimeoutMillis)
					.handler(new RpcClientInitializer());
			}
			return bootstrap
					.connect(remotePeer)
					.addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(final ChannelFuture channelFuture) throws Exception {
							if (channelFuture.isSuccess()) {
								LOGGER.info("Successfully connect to remote server. remote peer = " + remotePeer);
								RpcClientHandler handler = channelFuture.channel().pipeline().get(RpcClientHandler.class);
								handler.setRpcClient(rpcClient);
								addHandler(handler);
							}
							if(latch != null) {
								latch.countDown();
							}
						}
					});
		}
	}

	private void addHandler(RpcClientHandler handler) {
		InetSocketAddress remoteAddress = (InetSocketAddress) handler.getSession().getRemotePeer();
		Channel channel = handler.getSession().getChannel();
		if (connectedServerNodes.get(remoteAddress).size() < connectionPerClient) {
			connectedHandlers.add(handler);
			connectedServerNodes.put(remoteAddress, handler);
			signalAvailableHandler();
			LOGGER.info("new rpc client {} {}", channel, connectedHandlers.size());
		} else {
			handler.close();
			LOGGER.info("drop rpc client {} {}", channel, connectedHandlers.size());
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
		CopyOnWriteArrayList<RpcClientHandler> handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers
				.clone();
		int size = handlers.size();
		int tryTime = 0;
		while (isRuning && size <= 0 && tryTime < 2) {
			try {
				boolean available = waitingForHandler();
				tryTime++;
				if (available) {
					handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
					size = handlers.size();
				}
			} catch (InterruptedException e) {
				LOGGER.error("Waiting for available node is interrupted! ", e);
				throw new RpcTimeoutException("Can't connect any servers!", e);
			}
		}
		
		if(size == 0) {
			LOGGER.error("RpcClient choose handler fail! {}", this.serviceAddress);
			throw new RpcTimeoutException("RpcClient handler fetch timeout !");
		}
		
		int index = (roundRobin.getAndAdd(1) + size) % size;
		RpcClientHandler handler = handlers.get(index);
		LOGGER.debug("choose rpc handler index {}", index);
		return handler;
	}
	
	@SuppressWarnings("unchecked")
	public RpcClientHandler chooseHandler(SocketAddress remotePeer) {
		CopyOnWriteArrayList<RpcClientHandler> handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
		int size = handlers.size();
		int tryTime = 0;
		while (isRuning && size <= 0 && tryTime < 2) {
			try {
				boolean available = waitingForHandler();
				tryTime++;
				if (available) {
					handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlers.clone();
					size = handlers.size();
				}
			} catch (InterruptedException e) {
				LOGGER.error("Waiting for available node is interrupted! ", e);
				throw new RpcTimeoutException("Can't connect any servers!", e);
			}
		}
		
		if(size == 0) {
			LOGGER.error("RpcClient choose handler fail! {}", this.serviceAddress);
			throw new RpcTimeoutException("RpcClient handler fetch timeout !");
		}
		
		RpcClientHandler handler = null;
		if(connectionPerClient > 1) {
			List<RpcClientHandler> list = Lists.newArrayList();
			for(RpcClientHandler h : handlers) {
				if(h.getRemotePeer().equals(remotePeer)) {
					list.add(h);
				}
			}
			size = list.size();
			int index = (roundRobin.getAndAdd(1) + size) % size;
			handler = list.get(index);
		} else {
			for(RpcClientHandler h : handlers) {
				if(h.getRemotePeer().equals(remotePeer)) {
					handler = h;
				}
			}
		}
		return handler;
	}
	
	public int activeHandlers() {
		return connectedHandlers.size();
	}

	public void stop() {
		isRuning = false;
		for (int i = 0; i < connectedHandlers.size(); ++i) {
			RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
			connectedServerHandler.close();
		}
		signalAvailableHandler();
		if (serviceDiscovery != null) {
			serviceDiscovery.stop();
		}
		scheduler.shutdown();
		clientThreadPool.shutdown();
		clientEventLoopGroup.shutdownGracefully();
	}
	
	public String getServiceAddress() {
		return serviceAddress;
	}

	public boolean isCluster() {
		return cluster;
	}

	public void removeHandler(RpcClientHandler handler) {
		connectedHandlers.remove(handler);
		connectedServerNodes.remove(handler.getRemotePeer(), handler);
	}
	
	public RpcClient getRpcClient() {
		return rpcClient;
	}

	public void setRpcClient(RpcClient rpcClient) {
		this.rpcClient = rpcClient;
	}

	public int getConnectionPerClient() {
		return connectionPerClient;
	}

	public void setConnectionPerClient(int connectionPerClient) {
		this.connectionPerClient = connectionPerClient;
	}

	public long getConnectTimeoutMillis() {
		return connectTimeoutMillis;
	}

	public void setConnectTimeoutMillis(long connectTimeoutMillis) {
		this.connectTimeoutMillis = connectTimeoutMillis;
	}

	public void setZkRegistryPath(String zkRegistryPath) {
		this.zkRegistryPath = zkRegistryPath;
	}


	public ConcurrentHashMap<String, RpcFuture> getPendingRPC() {
		return pendingRPC;
	}


	/**
	 * 服务发现者
	 * @author jiangmin.wu 
	 */
	class ServiceDiscovery {
		private String registryAddress;
		private ZooKeeper zookeeper;

		private int zkTimeoutMillis = 5000;

		private CountDownLatch latch = new CountDownLatch(1);
		private volatile List<String> dataList = new ArrayList<>();

		public ServiceDiscovery(String registryAddress) {
			this.registryAddress = registryAddress;
			zookeeper = connectServer();
			if (zookeeper != null) {
				watchNode(zookeeper);
			}
		}

		public String discover() {
			String data = null;
			int size = dataList.size();
			if (size > 0) {
				if (size == 1) {
					data = dataList.get(0);
					LOGGER.debug("using only data: {}", data);
				} else {
					data = dataList.get(ThreadLocalRandom.current().nextInt(size));
					LOGGER.debug("using random data: {}", data);
				}
			}
			return data;
		}

		private ZooKeeper connectServer() {
			ZooKeeper zk = null;
			try {
				zk = new ZooKeeper(registryAddress, zkTimeoutMillis, new Watcher() {
					@Override
					public void process(WatchedEvent event) {
						if (event.getState() == Event.KeeperState.SyncConnected) {
							latch.countDown();
						}
					}
				});
				latch.await();
			} catch (IOException | InterruptedException e) {
				LOGGER.error("", e);
			}
			return zk;
		}

		private void watchNode(final ZooKeeper zk) {
			try {
				List<String> nodeList = zk.getChildren(zkRegistryPath, new Watcher() {
					@Override
					public void process(WatchedEvent event) {
						if (event.getType() == Event.EventType.NodeChildrenChanged) {
							watchNode(zk);
						}
					}
				});
				List<String> dataList = new ArrayList<>();
				for (String node : nodeList) {
					byte[] bytes = zk.getData(zkRegistryPath + "/" + node, false, null);
					dataList.add(new String(bytes));
				}
				LOGGER.debug("node data: {}", dataList);
				this.dataList = dataList;

				LOGGER.debug("Service discovery triggered updating connected server node.");
				updateServer();
			} catch (KeeperException | InterruptedException e) {
				LOGGER.error("", e);
			}
		}

		private void updateServer() {
			updateConnectedServer(this.dataList);
		}

		public void stop() {
			if (zookeeper != null) {
				try {
					zookeeper.close();
				} catch (InterruptedException e) {
					LOGGER.error("", e);
				}
			}
		}
	}
}
