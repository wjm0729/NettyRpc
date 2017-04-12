package com.nettyrpc.test.performance;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class PerfBootstrap {

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Throwable {
		new ClassPathXmlApplicationContext("server-spring.xml");
	}
}
