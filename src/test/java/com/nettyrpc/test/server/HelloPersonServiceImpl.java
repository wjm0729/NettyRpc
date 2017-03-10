package com.nettyrpc.test.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nettyrpc.server.RpcService;
import com.nettyrpc.test.client.HelloPersonService;
import com.nettyrpc.test.client.Person;

/**
 * Created by luxiaoxun on 2016-03-10.
 */
@RpcService(HelloPersonService.class)
public class HelloPersonServiceImpl implements HelloPersonService {

    @Override
    public List<Person> GetTestPerson(String name, int num) {
        List<Person> persons = new ArrayList<>(num);
        for (int i = 0; i < num; ++i) {
            persons.add(new Person(Integer.toString(i), name));
        }
        System.err.println("~~~~~~~~~~~~~~~~~~~~~"+name);
        
        
        return persons;
    }
    
    @Override
    public Map<String, Person> GetTestPerson2(String name, int num) {
    	Map<String, Person> persons = new HashMap<>();
    	for (int i = 0; i < num; ++i) {
    		persons.put(i+"", new Person(Integer.toString(i), name));
    	}
    	System.err.println("~~~~~~~~~~~~~~~~~~~~~"+name);
    	return persons;
    }
}
