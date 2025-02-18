package com.atguigu.daijia.order.Threads;

import java.util.concurrent.*;

public class CompletableFutureTest5 {

    public  static  void main(String[] args) throws ExecutionException, InterruptedException {
        //动态获取服务器核数

        int processors= Runtime.getRuntime().availableProcessors();
        System.out.println(processors);

        ThreadPoolExecutor threadPoolExecutor=new ThreadPoolExecutor(
                processors+1,
                processors+1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
                //corePoolSize (processors + 1)：
                //
                //这是线程池中核心线程的数量。在这个代码中，它是 processors + 1，其中 processors 是系统的 CPU 核心数。
                //corePoolSize 指定了线程池在没有任务时仍会保持活跃的线程数。即使没有任务可执行，线程池中的线程数也不会减少到低于 corePoolSize。
                //maximumPoolSize (processors + 1)：
                //
                //这是线程池中允许的最大线程数。在这个例子中，它和 corePoolSize 相同，都是 processors + 1。这意味着线程池的最大线程数不会超过 processors + 1。
                //keepAliveTime (0)：
                //
                //这是非核心线程在空闲时保持活动的时间。在这个例子中，它设置为 0，表示非核心线程在完成任务后立刻终止，不会保持活动状态。
                //这个参数只对非核心线程有效。对于核心线程，即使空闲，它们也不会被回收。
                //TimeUnit.SECONDS：
                //
                //这是 keepAliveTime 的时间单位。在这个例子中，0 秒表示非核心线程完成任务后立即终止。
                //workQueue (new ArrayBlockingQueue<>(10))：
                //
                //这是用于存储待执行任务的队列。在这个例子中，使用了 ArrayBlockingQueue，它是一个有界队列，最多可以容纳 10 个任务。
                //任务在等待执行时会被放入这个队列中。如果队列满了且没有可用线程，新的任务将被拒绝。
                //threadFactory (Executors.defaultThreadFactory())：
                //
                //线程池的线程工厂，用于创建新线程。在这里，使用了 Executors.defaultThreadFactory()，它会创建一个默认的线程，通常为后台线程并且没有特殊的命名。
                //通过自定义线程工厂，可以为线程池中的线程指定特定的名称、优先级等属性。
                //handler (new ThreadPoolExecutor.AbortPolicy())：
                //
                //这是一个拒绝策略，用于处理当线程池无法接受新任务时的情况。在这个例子中，使用了 AbortPolicy，它会在无法执行任务时抛出一个 RejectedExecutionException 异常。
                //其他常见的拒绝策略包括：
                //CallerRunsPolicy：让调用线程自行处理任务。
                //DiscardPolicy：丢弃当前任务。
                //DiscardOldestPolicy：丢弃最旧的未处理任务
        );

        CompletableFuture<String> future01 = CompletableFuture.supplyAsync(() -> "任务一",threadPoolExecutor);
        CompletableFuture<String> future02 = CompletableFuture.supplyAsync(() -> "任务2", threadPoolExecutor);

        CompletableFuture<String> future03 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "任务三";
        }, threadPoolExecutor);

        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.allOf(future01, future02, future03);

        // 等待所有线程执行完成
        // .join()和.get()都会阻塞并获取线程的执行情况
        // .join()会抛出未经检查的异常，不会强制开发者处理异常 .get()会抛出检查异常，需要开发者处理
        voidCompletableFuture.join();
        voidCompletableFuture.get();

    }
}
