package com.nettyrpc.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.google.common.collect.Maps;
import com.nettyrpc.execution.ActionExecutor;
import com.nettyrpc.protocol.PingPongHandler;
import com.nettyrpc.protocol.RpcDecoder;
import com.nettyrpc.protocol.RpcEncoder;
import com.nettyrpc.protocol.RpcRequest;
import com.nettyrpc.protocol.RpcResponse;
import com.nettyrpc.protocol.TimeoutHandler;
import com.nettyrpc.registry.ServiceRegistry;
import com.nettyrpc.thread.NamedThreadFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * RPC Server
 * @author huangyong,luxiaoxun
 * @author jiangmin.wu
 */
public class RpcServer implements ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServer.class);

    private ChannelFuture future;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    private String serverAddress;
    private ServiceRegistry serviceRegistry;
    private Map<String, Object> handlerMap = new HashMap<>();
    
    private int core = Runtime.getRuntime().availableProcessors();
    // 默认 线程数 core * 2 -> core * 4, 队列长度 65535, 满溢丢弃策略
    private ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(core * 2, core * 4, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65535), new NamedThreadFactory("RpcServer"));
    // 异步消息消费线程池
    private ActionExecutor asyncActionExecutor = new ActionExecutor(threadPoolExecutor);
    // 异步消息处理器
    private Map<String, AsyncServerHandler> asyncServerHandlerMap = Maps.newConcurrentMap();
    // 多少秒没有读写事件就断开连接
    private int clientTimeoutSeconds = 180;
    private IRpcHandlerProxy rpcHandlerProxy = IRpcHandlerProxy.DEFAULT;
    
    /**
     * 不需要集群
     * 
     * @param serverAddress
     */
    public RpcServer(String serverAddress) {
    	this(serverAddress, null);
    }
    
    public RpcServer(String serverAddress, ServiceRegistry serviceRegistry) {
    	this.serverAddress = serverAddress;
    	this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                String interfaceName = serviceBean.getClass().getAnnotation(RpcService.class).value().getName();
                handlerMap.put(interfaceName, serviceBean);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    	bossGroup = new NioEventLoopGroup();
    	workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        final RpcServer rpcServer = this;
        bootstrap.group(bossGroup, workerGroup)
        		.channel(NioServerSocketChannel.class)
        		.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        		.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline()
		                        .addLast(new LengthFieldBasedFrameDecoder(1024 * 1024 * 64, 1, 4, 0, 0))
                                .addLast(new RpcDecoder(RpcRequest.class))
                                .addLast(new RpcEncoder(RpcResponse.class))
                                .addLast(new IdleStateHandler(clientTimeoutSeconds, clientTimeoutSeconds, clientTimeoutSeconds, TimeUnit.SECONDS))
                                .addLast(new TimeoutHandler())
                                .addLast(new PingPongHandler())
                                .addLast(new RpcHandler(rpcServer));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        String[] array = serverAddress.split(":");
        String host = array[0];
        int port = Integer.parseInt(array[1]);

        future = bootstrap.bind(host, port).sync();
        LOGGER.debug("Server started on port {}", port);

        if (serviceRegistry != null) {
            serviceRegistry.register(serverAddress);
        }
    }

	public void submit(Runnable task) {
		if (!threadPoolExecutor.isShutdown()) {
			threadPoolExecutor.submit(task);
		}
	}

	public Map<String, Object> getHandlerMap() {
		return handlerMap;
	}

	public IRpcHandlerProxy getRpcHandlerProxy() {
		return rpcHandlerProxy;
	}

	@Override
	public void destroy() throws Exception {
		try {
			if (!threadPoolExecutor.isShutdown()) {
				threadPoolExecutor.shutdown();
			}
			future.channel().close().awaitUninterruptibly();
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}
	
	public void registerAsyncServerHandler(String id, AsyncServerHandler handler) {
		asyncServerHandlerMap.put(id, handler);
	}

	// for spring
	public void setClientTimeoutSeconds(int clientTimeoutSeconds) {
		this.clientTimeoutSeconds = clientTimeoutSeconds;
	}

	public ThreadPoolExecutor getThreadPoolExecutor() {
		return threadPoolExecutor;
	}

	public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
		this.threadPoolExecutor = threadPoolExecutor;
	}

	public void setRpcHandlerProxy(IRpcHandlerProxy rpcHandlerProxy) {
		this.rpcHandlerProxy = rpcHandlerProxy;
	}

	public ActionExecutor getAsyncActionExecutor() {
		return asyncActionExecutor;
	}

	public void setAsyncActionExecutor(ActionExecutor asyncActionExecutor) {
		this.asyncActionExecutor = asyncActionExecutor;
	}

	public Map<String, AsyncServerHandler> getAsyncServerHandlerMap() {
		return asyncServerHandlerMap;
	}
}
