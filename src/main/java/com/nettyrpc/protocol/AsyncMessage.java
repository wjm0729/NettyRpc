package com.nettyrpc.protocol;

public class AsyncMessage extends AbastractMessage {
	private Object body;

	public Object getBody() {
		return body;
	}
	
	public <T> T getBodyObj() {
		return (T)body;
	}

	public void setBody(Object body) {
		this.body = body;
	}

	@Override
	public String toString() {
		return "AsyncMessage [id=" + getRequestId() + ", body=" + body + "]";
	}

}
