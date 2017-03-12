package com.nettyrpc.execution;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 执行队列的线程池封装<br>
 * 
 * @author jiangmin.wu
 *
 */
public class ActionExecutor {
	private static Logger logger = LoggerFactory.getLogger(ActionExecutor.class);
	private ExecutorService pool;
	private DelayCheckThread delayCheckThread;
	private String name;
	
	public ActionExecutor(ExecutorService pool) {
		this.pool = pool;
	}

	public ActionExecutor(int corePoolSize, int maxPoolSize, int keepAliveTime, int cacheSize, String threadName) {
		this(corePoolSize, maxPoolSize, keepAliveTime, cacheSize, threadName, null);
	}

	public ActionExecutor(int corePoolSize, int maxPoolSize, int keepAliveTime, int cacheSize, String threadName, RejectedExecutionHandler rejected) {
		if (cacheSize <= 0) {
			cacheSize = Integer.MAX_VALUE;
		}
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(cacheSize);
		if (rejected == null) {
			rejected = new ThreadPoolExecutor.CallerRunsPolicy();
		}
		if (threadName == null) {
			threadName = "";
			this.name = getClass().getSimpleName();
		} else {
			this.name = threadName;
		}
		pool = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, TimeUnit.MINUTES, workQueue, new Threads(threadName), rejected);
	}

	public void updateMaximumPoolSize(int value) {
		ThreadPoolExecutor tp = (ThreadPoolExecutor) pool;
		if (value != tp.getMaximumPoolSize()) {
			tp.setCorePoolSize(value);
			tp.setMaximumPoolSize(value);
			logger.info("{} update max pool size to {} success!!!", name, value);
		}
	}

	public void execute(Action action) {
		pool.execute(action);
	}

	public void shutdown() {
		if (!pool.isShutdown()) {
			pool.shutdown();
		}

		if (delayCheckThread != null)
			delayCheckThread.stopping();
	}

	public void delay(DelayAction action) {
		if (this.delayCheckThread == null) {
			configureDelayCheckTread();
		}

		this.delayCheckThread.enqueue(action);
	}

	synchronized void configureDelayCheckTread() {
		if (this.delayCheckThread == null) {
			this.delayCheckThread = new DelayCheckThread(name);
			this.delayCheckThread.start();
		}
	}

	public ThreadPoolExecutor getPool() {
		return (ThreadPoolExecutor)pool;
	}
	
	public String getName() {
		return name;
	}

	static class DelayCheckThread extends Thread {

		private static final int CHECK_TIME = 40;// ms
		private ConcurrentLinkedDeque<DelayAction> queue;
		private boolean isRunning;

		public DelayCheckThread(String prefix) {
			super(prefix + "-DelayCheckThread");
			queue = new ConcurrentLinkedDeque<>();
			isRunning = true;
			setPriority(Thread.MAX_PRIORITY); // 给予高优先级
		}

		public boolean isRunning() {
			return isRunning;
		}

		public void stopping() {
			if (isRunning) {
				isRunning = false;
			}
		}

		@Override
		public void run() {
			while (isRunning) {
				try {
					long zizzTime = CHECK_TIME;
					if (!this.queue.isEmpty()) {
						long start = System.currentTimeMillis();
						int checked = checkActions();
						long interval = System.currentTimeMillis() - start;
						zizzTime -= interval;
						if (interval > CHECK_TIME) {
							logger.warn(getName() + " is spent too much time: " + interval + "ms, checked num = " + checked);
						}
					}

					if (zizzTime > 0) {
						TimeUnit.MILLISECONDS.sleep(zizzTime);
					}
				} catch (Exception e) {
					logger.error(getName() + " Error. ", e);
				}
			}
		}

		/**
		 * 返回检查完成的Action数量
		 **/
		public int checkActions() {
			DelayAction last = this.queue.peekLast();
			if (last == null) {
				return 0;
			}

			int checked = 0;
			DelayAction current = null;

			while ((current = this.queue.removeFirst()) != null) {
				try {
					long begin = System.currentTimeMillis();
					if (!current.tryExec(begin)) {
						enqueue(current);
					}
					checked++;
					long end = System.currentTimeMillis();
					if (end - begin > CHECK_TIME) {
						logger.warn(current.toString() + " spent too much time. time :" + (end - begin));
					}
					if (current == last) {
						break;
					}
				} catch (Exception e) {
					logger.error("检测 action delay 异常" + current.toString(), e);
				}
			}
			return checked;
		}

		/**
		 * 添加Action到队列
		 * 
		 * @param delayAction
		 */
		public void enqueue(DelayAction delayAction) {
			queue.addLast(delayAction);
		}

	}

	static class Threads implements ThreadFactory {
		static final AtomicInteger poolNumber = new AtomicInteger(1);
		final ThreadGroup group;
		final AtomicInteger threadNumber = new AtomicInteger(1);
		final String namePrefix;

		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(group, runnable, (new StringBuilder()).append(namePrefix).append(threadNumber.getAndIncrement()).toString(), 0L);
			if (thread.isDaemon())
				thread.setDaemon(false);
			if (thread.getPriority() != 5)
				thread.setPriority(5);
			return thread;
		}

		Threads(String prefix) {
			SecurityManager securitymanager = System.getSecurityManager();
			group = securitymanager == null ? Thread.currentThread().getThreadGroup() : securitymanager.getThreadGroup();
			namePrefix = (new StringBuilder()).append("pool-").append(poolNumber.getAndIncrement()).append("-").append(prefix).append("-thread-").toString();
		}
	}
}
