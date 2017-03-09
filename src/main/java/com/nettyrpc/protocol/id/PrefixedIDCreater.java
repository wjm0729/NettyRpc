package com.nettyrpc.protocol.id;

import java.util.UUID;

/**
 * 带固定前缀的id生成器 prefix_uuid
 * 
 * @author jiangmin.wu
 */
public class PrefixedIDCreater implements IRequestIDCreater {
	private String prefix;

	public PrefixedIDCreater(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public String crreateID() {
		return prefix + "_" + UUID.randomUUID().toString();
	}
}
