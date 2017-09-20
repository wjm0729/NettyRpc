package com.nettyrpc.protocol;

@SuppressWarnings("serial")
public class RpcTimeoutException extends RuntimeException {

	public RpcTimeoutException(String exception) {
		super(exception);
	}

	public RpcTimeoutException(String exception, InterruptedException e) {
		super(exception, e);
	}
}
