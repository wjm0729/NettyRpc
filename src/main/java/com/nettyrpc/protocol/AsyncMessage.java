package com.nettyrpc.protocol;

public class AsyncMessage extends AbastractMessage {
	private Object body;

	public Object getBody() {
		return body;
	}

	public void setBody(Object body) {
		this.body = body;
	}

	@Override
	public String toString() {
		return "AsyncMessage [body=" + body + "]";
	}
}
