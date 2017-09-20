package com.nettyrpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.protocol.AsyncMessage;

public interface AsyncClientHandler {
	Logger logger = LoggerFactory.getLogger(AsyncClientHandler.class);
	
	AsyncClientHandler DEFAULT = new AsyncClientHandler(){

		@Override
		public void handMessage(AsyncClient client, AsyncMessage message) {
			logger.debug("recieve {}", message);
		}
	};
	
	void handMessage(AsyncClient client, AsyncMessage message);
}
