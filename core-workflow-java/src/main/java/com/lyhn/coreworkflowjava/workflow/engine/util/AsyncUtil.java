package com.lyhn.coreworkflowjava.workflow.engine.util;
import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步工具类
 */
// 保证了调用方的逻辑不会被无限期阻塞，同时在大部分情况下让超时的任务自身自灭
@Slf4j
public class AsyncUtil {
    private static ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = this.defaultFactory.newThread(r);
            if(!thread.isDaemon()){
                // 不会阻止应用关闭，避免因异步任务无法退出而影响主应用
                thread.setDaemon(true);
            }
            thread.setName("workflow-" + this.threadNumber.getAndIncrement());
            return thread;
        }
    };
    // 线程池执行器服务变量，用于管理和执行异步任务
    private static ExecutorService executorService;
    // Google Guava库提供的时间限制器
    private static SimpleTimeLimiter simpleTimeLimiter;

    static {
        initExecutorService(Runtime.getRuntime().availableProcessors() * 2,50);
    }

    public static void initExecutorService(int core, int max) {
        // 异步工具类的默认线程池构建, 参数选择原则:
        //  1. IntelligentFlow不存在cpu密集型任务，大部分操作都设计到 redis/mysql 等io操作
        //  2. 统一的异步封装工具，这里的线程池是一个公共的执行仓库，不希望被其他的线程执行影响，因此队列长度为0, 核心线程数满就创建线程执行，超过最大线程，就直接当前线程执行
        //  3. 同样因为属于通用工具类，再加上IntelligentFlow不存在cpu密集型任务的异步使用的情况实际上并不是非常饱和的，因此空闲线程直接回收掉即可；大部分场景下，cpu * 2的线程数即可满足要求了
        max = Math.max(core, max);
        executorService = new ExecutorBuilder()
                .setCorePoolSize(core)
                .setMaxPoolSize(max)
                .setKeepAliveTime(0)
                .setKeepAliveTime(0,TimeUnit.SECONDS)
                .setWorkQueue(new SynchronousQueue<Runnable>())
                .setHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                .setThreadFactory(THREAD_FACTORY)// 决定了线程池如何创建线程
                .build();
        // 包装一下线程池，避免出现上下文复用场景
        executorService = TtlExecutors.getTtlExecutorService(executorService);
        // simpleTimeLimiter会与executorService绑定
        simpleTimeLimiter = SimpleTimeLimiter.create(executorService);
    }

    /**
     * 带超时时间的方法调用执行，当执行时间超过给定的时间，则返回一个超时异常，内部的任务还是正常执行
     * 若超时时间内执行完毕，则直接返回
     *
     * @param time
     * @param unit
     * @param call
     * @param <T>
     * @return
     */
    /**
     * 带超时时间的方法调用执行，当执行时间超过给定的时间，则返回一个超时异常，内部的任务还是正常执行
     * 若超时时间内执行完毕，则直接返回
     *
     * @param time
     * @param unit
     * @param call
     * @param <T>
     * @return
     */
    public static <T> T callWithTimeLimit(long time, TimeUnit unit, Callable<T> call) throws ExecutionException, InterruptedException, TimeoutException {
        // 接收一个 Callable<T> 对象，并将其提交到它在初始化时绑定的 executorService 线程池中执行
        return simpleTimeLimiter.callWithTimeout(call, time, unit);
    }

    public static void execute(Runnable call){
        executorService.execute(call);
    };

    public static <T> Future<T> submit(Callable<T> t) {
        return executorService.submit(t);
    }

    // 新增一个sleep的方法
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("线程被中断", e);
            Thread.currentThread().interrupt();
        }
    }
}