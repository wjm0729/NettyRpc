package com.nettyrpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.protocol.AsyncMessage;

public interface AsyncClientHandler {
	Logger logger = LoggerFactory.getLogger(AsyncClientHandler.class);
	
	AsyncClientHandler DEFAULT = new AsyncClientHandler(){

		@Override
		public void handMessage(AsyncMessage message, ClientSession session) {
			logger.info("recieve {}", message);
		}
	};
	
	
	void handMessage(AsyncMessage message, ClientSession session);

}
