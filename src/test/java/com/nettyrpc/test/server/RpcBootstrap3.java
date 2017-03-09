package com.nettyrpc.test.server;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RpcBootstrap3 {

    public static void main(String[] args) {
        new ClassPathXmlApplicationContext("server-spring3.xml");
    }
}
