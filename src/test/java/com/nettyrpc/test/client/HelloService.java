package com.nettyrpc.test.client;

public interface HelloService {
	
	String hello(int i);
	
    String hello(String name);

    String hello(Person person);
    
}
