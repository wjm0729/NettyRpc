package com.nettyrpc.client;

public interface IRpcClientListener {
	IRpcClientListener DEFAULT = new IRpcClientListener() {
	};

	default void onReceive(AsyncClient client, Object message) {
	}

	default void onInactive(AsyncClient client) {
	}

	default void onEventTriggered(AsyncClient client, Object evt) {
	}

	default void onActive(AsyncClient client) {
	}
}
