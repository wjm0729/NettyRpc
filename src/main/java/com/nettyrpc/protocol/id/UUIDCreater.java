package com.nettyrpc.protocol.id;

import java.util.UUID;

/**
 * Uuid 
 *  
 * @author jiangmin.wu 
 */
public class UUIDCreater implements IRequestIDCreater {

	@Override
	public String crreateID() {
		return UUID.randomUUID().toString();
	}

}
