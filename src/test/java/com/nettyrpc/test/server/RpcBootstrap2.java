package com.nettyrpc.test.server;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RpcBootstrap2 {

    public static void main(String[] args) {
        new ClassPathXmlApplicationContext("server-spring2.xml");
    }
}
