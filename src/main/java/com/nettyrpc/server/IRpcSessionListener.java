package com.nettyrpc.server;

public interface IRpcSessionListener {
	IRpcSessionListener DEFAULT = new IRpcSessionListener() {
	};

	default void onActive(AsyncSession session) {

	}

	default void onInactive(AsyncSession session) {

	}

	default void onReceive(AsyncSession session, Object message) {

	}

	default void onEventTriggered(AsyncSession session, Object evt) {

	}
}
