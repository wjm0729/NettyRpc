package com.nettyrpc.protocol.id;

/**
 * requestid 生成器
 * 
 * @author jiangmin.wu
 */
public interface IRequestIDCreater {
	
	IRequestIDCreater DEFAULT = new UUIDCreater();
	
	String crreateID();
}
