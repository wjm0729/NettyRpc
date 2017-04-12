package com.nettyrpc.test.performance;

import com.nettyrpc.server.RpcService;
import com.nettyrpc.test.performance.model.RaceDO;

@RpcService(IPerfService.class)
public class PerfServerImpl implements IPerfService {

	@Override
	public RaceDO getRaceDO() {
		return new RaceDO();
	}

}
