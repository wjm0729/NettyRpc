package com.nettyrpc.test.server;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RpcBootstrap {

	public static void main(String[] args) throws Throwable {
		new ClassPathXmlApplicationContext("server-spring.xml");
	}
}
