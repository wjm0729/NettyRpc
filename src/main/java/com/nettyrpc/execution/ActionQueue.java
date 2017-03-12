package com.nettyrpc.execution;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 执行的队列
 * 
 * @author jiangmin.wu
 *
 */
public class ActionQueue {
	private static final Logger logger = LoggerFactory.getLogger(ActionQueue.class);

	protected ActionExecutor executor;
	protected Queue<Action> queue;
	private AtomicBoolean isRunning = new AtomicBoolean(false);

	public ActionQueue(ActionExecutor executor) {
		this.executor = executor;
		this.queue = new ConcurrentLinkedQueue<Action>();
	}

	public void delay(DelayAction action) {
		executor.delay(action);
	}

	public void enqueue(Action action) {
		beforeEnqueue(action);
		queue.offer(action);
		if (isRunning.compareAndSet(false, true)) {
			execNext();
		}
	}

	private void beforeEnqueue(Action action) {
		// TODO Auto-generated method stub
	}

	public void dequeue(Action action) {
		Action tmp = queue.poll();
		if (tmp != action) {
			logger.error("{} queue {} got wrong when {} dequeue.", executor.getName(), this, action);
		}
		execNext();
	}

	private void execNext() {
		Action next = queue.peek();
		if (next != null) {
			executor.execute(next);
		} else {
			isRunning.set(false);

			// double check
			next = queue.peek();
			if (next != null && isRunning.compareAndSet(false, true)) {
				executor.execute(next);
			}
		}
	}

	public void clear() {
		queue.clear();
	}

	public Queue<Action> getQueue() {
		return queue;
	}
}
