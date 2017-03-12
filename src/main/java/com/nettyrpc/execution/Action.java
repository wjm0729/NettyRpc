package com.nettyrpc.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 执行单元
 * 
 * @author jiangmin.wu
 *
 */
public abstract class Action implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(Action.class);

	protected ActionQueue queue;
	protected Long createTime;

	public Action(ActionQueue queue) {
		this.queue = queue;
		createTime = System.currentTimeMillis();
	}

	public void enqueue() {
		queue.enqueue(this);
	}

	@Override
	public void run() {
		if (queue != null) {
			long start = System.currentTimeMillis();
			try {
				execute();
				long end = System.currentTimeMillis();
				long interval = end - start;
				long leftTime = end - createTime;
				if (interval >= 1000) {
					logger.warn("execute action : " + this.toString() + ", interval : " + interval + ", leftTime : "
							+ leftTime + ", size : " + queue.getQueue().size());
				}
			} catch (Throwable e) {
				logger.error("run action execute exception. action : " + this.toString(), e);
			} finally {
				queue.dequeue(this);
			}
		}
	}

	public abstract void execute() throws Throwable;
}
