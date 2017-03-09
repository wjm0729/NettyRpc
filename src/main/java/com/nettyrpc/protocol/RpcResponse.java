package com.nettyrpc.protocol;

/**
 * RPC Response
 * @author huangyong
 */
public class RpcResponse extends RpcMessage {

    private Throwable error;
    private Object result;

    public boolean isError() {
        return error != null;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
