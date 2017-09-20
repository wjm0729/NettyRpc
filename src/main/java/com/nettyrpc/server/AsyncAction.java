package com.nettyrpc.server;

import com.nettyrpc.execution.Action;
import com.nettyrpc.execution.ActionQueue;
import com.nettyrpc.protocol.AsyncMessage;

public class AsyncAction extends Action {
	private AsyncMessage message;
	private AsyncSession client;
	
	public AsyncAction(ActionQueue queue, AsyncMessage message, AsyncSession client) {
		super(queue);
		this.message = message;
		this.client = client;
	}

	@Override
	public void execute() throws Throwable {
		AsyncServerHandler handler = client.getRpcServer().getAsyncServerHandlerMap().get(message.getRequestId());
		if(handler != null) {
//			handler = SerializationUtil.objenesis.newInstance(handler.getClass());
			handler.handMessage(client, message);
		} else {
			AsyncServerHandler.DEFAULT.handMessage(client, message);
		}
	}
}
