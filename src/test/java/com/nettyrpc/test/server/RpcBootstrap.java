package com.nettyrpc.test.server;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nettyrpc.protocol.AsyncMessage;
import com.nettyrpc.server.AsyncClient;
import com.nettyrpc.server.AsyncServerHandler;
import com.nettyrpc.server.RpcServer;

public class RpcBootstrap {

	public static void main(String[] args) throws Throwable {
		ApplicationContext ctx = new ClassPathXmlApplicationContext("server-spring.xml");
		RpcServer rpcServer = ctx.getBean(RpcServer.class);

		rpcServer.registerAsyncServerHandler("aaa", new AsyncServerHandler() {

			@Override
			public void handMessage(AsyncClient client, AsyncMessage message) {
				System.err.println("=============" + message + "=================");
				client.sendMessge(message);
			}
		});
	}
}
