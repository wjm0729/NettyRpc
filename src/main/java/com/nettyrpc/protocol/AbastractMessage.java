package com.nettyrpc.protocol;

public abstract class AbastractMessage implements IMessage {
	private String requestId;

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
}
