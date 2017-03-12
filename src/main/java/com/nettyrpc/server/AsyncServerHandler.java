package com.nettyrpc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nettyrpc.protocol.AsyncMessage;

public interface AsyncServerHandler {
	Logger logger = LoggerFactory.getLogger(AsyncServerHandler.class);
	
	AsyncServerHandler DEFAULT = new AsyncServerHandler() {
		
		@Override
		public void handMessage(AsyncClient client, AsyncMessage message) {
			logger.info("receive {}", message);
			client.sendMessge(message);
		}
	};
	
	/**
	 * 基于channel 顺序执行的
	 * 
	 * @param client
	 * @param message
	 */
	void handMessage(AsyncClient client, AsyncMessage message);
	
}
