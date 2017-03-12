package com.nettyrpc.execution;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class InstrumentedThreadPoolExecutor extends ThreadPoolExecutor {
	private Map<Runnable, Long> timeOfRequest = new ConcurrentHashMap<>();
	private ThreadLocal<Long> startTime = new ThreadLocal<Long>();
	// 上一次请求到达时间
	private volatile long lastArrivalTime;
	// 总共执行时间
	private AtomicLong totalServiceTime = new AtomicLong();
	// 队列总停留时间
	private AtomicLong totalPoolTime = new AtomicLong();
	// 总请求数量
	private AtomicLong numberOfRequests = new AtomicLong();
	// 处理完成的请求数量
	private AtomicLong numberOfRequestsRetired = new AtomicLong();
	// 总请求到达间隔时间  ( 到达间隔时间 )
	private AtomicLong aggregateInterRequestArrivalTime = new AtomicLong();
	
	public InstrumentedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
            RejectedExecutionHandler rejectedHandler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, rejectedHandler);
	}

	// //////////////////////////////////////////////////////////////////
	/**
	 * 到达率 = 请求数 / 间隔时间S
	 */
	public double getRequestPerSecondRetirementRate() {
		return getNumberOfRequestsRetired().longValue() / TimeUnit.NANOSECONDS.toSeconds(getAggregateInterRequestArrivalTime().longValue());
	}

	/**
	 * 平均处理时间 = 总处理时间 / 处理数
	 */
	public double getAverageServiceTime() {
		return TimeUnit.NANOSECONDS.toSeconds(getTotalServiceTime().longValue()) / getNumberOfRequestsRetired().longValue();
	}

	/**
	 * 平均等待处理时间
	 * @return
	 */
	public double getAverageTimeWaitingInPool() {
		return TimeUnit.NANOSECONDS.toSeconds(getTotalPoolTime().longValue()) / getNumberOfRequestsRetired().longValue();
	}

	/**
	 * 平均应答时间 = 平均等待处理时间 + 平均处理时间
	 * @return
	 */
	public double getAverageResponseTime() {
		return this.getAverageServiceTime() + this.getAverageTimeWaitingInPool();
	}

	/**
	 * 利特尔法则（Little’s law）的实现
	 * 
	 * 处理数 = 到达率 * 应答时间
	 */
	public double getEstimatedAverageNumberOfActiveRequests() {
		return getRequestPerSecondRetirementRate() * getAverageResponseTime();
	}

	/**
	 * 死区时间比
	 * @return
	 */
	public double getRatioOfDeadTimeToResponseTime() {
		double poolTime = getTotalPoolTime().longValue();
		return poolTime / (poolTime + getTotalServiceTime().longValue());
	}

	/**
	 * 处理数 / 核心数
	 * @return
	 */
	public double v() {
		return getEstimatedAverageNumberOfActiveRequests() / Runtime.getRuntime().availableProcessors();
	}
	// //////////////////////////////////////////////////////////////////

	@Override
	protected void beforeExecute(Thread worker, Runnable task) {
		super.beforeExecute(worker, task);
		startTime.set(System.nanoTime());
	}

	@Override
	protected void afterExecute(Runnable task, Throwable t) {
		try {
			totalServiceTime.addAndGet(System.nanoTime() - startTime.get());
			totalPoolTime.addAndGet(startTime.get() - timeOfRequest.remove(task));
			numberOfRequestsRetired.incrementAndGet();
		} finally {
			super.afterExecute(task, t);
		}
	}

	@Override
	public void execute(Runnable task) {
		long now = System.nanoTime();
		synchronized (this) {
			if (lastArrivalTime != 0L) {
				aggregateInterRequestArrivalTime.addAndGet(now - lastArrivalTime);
			}
			lastArrivalTime = now;
		}
		numberOfRequests.incrementAndGet();
		timeOfRequest.put(task, now);
		super.execute(task);
	}

	/**
	 * 线程处理时间总和
	 * @return
	 */
	public AtomicLong getTotalServiceTime() {
		return totalServiceTime;
	}

	/**
	 * 在队列等待的总时间
	 * @return
	 */
	public AtomicLong getTotalPoolTime() {
		return totalPoolTime;
	}

	/**
	 * 总请求数
	 * @return
	 */
	public AtomicLong getNumberOfRequests() {
		return numberOfRequests;
	}

	/**
	 * 已经处理的请求数
	 * @return
	 */
	public AtomicLong getNumberOfRequestsRetired() {
		return numberOfRequestsRetired;
	}

	/**
	 * 总请求到达时间
	 * @return
	 */
	public AtomicLong getAggregateInterRequestArrivalTime() {
		return aggregateInterRequestArrivalTime;
	}
}