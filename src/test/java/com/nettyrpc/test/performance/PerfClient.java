package com.nettyrpc.test.performance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.nettyrpc.client.ConnectManage;
import com.nettyrpc.client.RpcClient;
import com.nettyrpc.protocol.SerializationUtil;
import com.nettyrpc.test.performance.model.RaceDO;

/**
 * 吞吐量测试
 * 
 * 
 * 
 * @author jiangmin.wu
 *         <p>
 *         2017年4月11日 下午7:29:45
 *         </p>
 */
public class PerfClient {

	public static void main(String[] args) throws Exception {

		OutputStream out = new FileOutputStream(new File("./performance.log"));

		int coreCount = Runtime.getRuntime().availableProcessors() * 2 * 2;

		final CountDownLatch countDownLatch = new CountDownLatch(coreCount);
		final AtomicLong callAmount = new AtomicLong(0L);

		ConnectManage connectManage = new ConnectManage("10.1.6.72:2181", true);
		final RpcClient rpcClient = new RpcClient(connectManage);
		rpcClient.afterPropertiesSet();

		final IPerfService service = rpcClient.create(IPerfService.class);

		final ExecutorService executor = Executors.newFixedThreadPool(coreCount);

		RaceDO raceDO = new RaceDO();

		byte[] data = SerializationUtil.serialize(raceDO);
		SerializationUtil.deserialize(data, RaceDO.class);

		long startTime = System.currentTimeMillis();

		for (int i = 0; i < coreCount; i++) {
			executor.execute(new Runnable() {
				public void run() {
					while (callAmount.get() < 100000) {
						try {
							RaceDO ret = service.getRaceDO();
							if (ret != null) {
								callAmount.incrementAndGet();
							} else {
								continue;
							}
						} catch (Exception e) {
							continue;
						}
					}
					countDownLatch.countDown();
				}
			});
		}

		countDownLatch.await(300, TimeUnit.SECONDS);

		if (callAmount.intValue() < 100000) {
			out.write("Doesn't finish all invoking.".getBytes());
		} else {
			long endTime = System.currentTimeMillis();
			Float tps = (float) callAmount.get() / (float) (endTime - startTime) * 1000F;
			StringBuilder sb = new StringBuilder();
			sb.append("tps:").append(tps);
			out.write(sb.toString().getBytes());
			out.close();
		}

		rpcClient.stop();
		executor.shutdown();
	}
}
