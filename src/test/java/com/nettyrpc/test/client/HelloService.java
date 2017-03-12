package com.nettyrpc.test.client;

import com.example.Booking;

public interface HelloService {
	
	String hello(int i);
	
    String hello(String name);

    String hello(Person person);
    
    Booking getBook();
}
