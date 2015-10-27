package bboss.org.jgroups.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ThreadPoolExecutor subclass that implements @{link ThreadManager}.
 * @author Brian Stansberry
 * @version $Id: ThreadManagerThreadPoolExecutor.java,v 1.3 2008/09/22 13:54:54 belaban Exp $
 */
public class ThreadManagerThreadPoolExecutor extends ThreadPoolExecutor implements ThreadManager {
    private ThreadDecorator decorator;

    public ThreadManagerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                           BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ThreadManagerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                           BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ThreadManagerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                           BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ThreadManagerThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                           BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public ThreadDecorator getThreadDecorator() {
        return decorator;
    }

    public void setThreadDecorator(ThreadDecorator decorator) {
        this.decorator=decorator;
    }

    /**
     * Invokes {@link ThreadDecorator#threadReleased(Thread)} on the current thread.
     * <p/>
     * {@inheritDoc}
     */
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            super.afterExecute(r, t);
        }
        finally {
            if(decorator != null)
                decorator.threadReleased(Thread.currentThread());
        }
    }

}
