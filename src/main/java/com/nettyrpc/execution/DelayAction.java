package com.nettyrpc.execution;

/**
 * 延迟执行单元
 * 
 * @author jiangmin.wu
 *
 */
public abstract class DelayAction extends Action {
    long execTime;
    volatile boolean cancel;
    
    DelayAction(ActionQueue queue, long execTime) {
        super(queue);
        this.cancel = false;
        this.execTime = execTime;
    }
    
    /**
     * @param queue
     * @param delay	ms
     */
	public DelayAction(ActionQueue queue, int delay) {
	    this(queue, System.currentTimeMillis(), delay);
	}
	
	/**
	 * @param queue
	 * @param curTime
	 * @param delay	ms
	 */
	public DelayAction(ActionQueue queue, long curTime, int delay) {
		super(queue);
		calExecTime(curTime, delay);
	}

    @Override
	public void run() {
		if(cancel) return;
        super.run();
	}

	@Override
    public void enqueue() {
	    if(this.execTime == 0) {
	        queue.enqueue(this);
	    } else {
	        queue.delay(this);
	    }
    }
	
	public void cancel() {
	    this.cancel = true;
	}
	
	public void delay(int delay) {
		this.delay(System.currentTimeMillis(), delay);
	}
	
	public void delay(long curTime, int delay) {
		calExecTime(curTime, delay);
		enqueue();
	}
	
	private void calExecTime(long curTime, int delay) {
		if(delay > 0) {
		    this.execTime = curTime + delay;
		} else {
		    this.createTime = curTime;
		    this.execTime = 0;
		}
	}

    public boolean tryExec(long curTime) {
        if(cancel) {
            return true;
        }
		if(curTime >= execTime) {
			createTime = curTime;
			queue.enqueue(this);
			return true;
		}
		return false;
	}
}
