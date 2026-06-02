package com.shopmall.cs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE 线程池 Bean
 * 独立于 Controller 生命周期，参数可配置
 */
@Configuration
public class SseExecutorConfig {

    @Value("${cs.sse.core-pool-size:4}")
    private int corePoolSize;

    @Value("${cs.sse.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${cs.sse.queue-capacity:100}")
    private int queueCapacity;

    @Value("${cs.sse.keep-alive-seconds:60}")
    private long keepAliveSeconds;

    @Bean(name = "sseExecutor", destroyMethod = "shutdown")
    public ExecutorService sseExecutor() {
        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "sse-worker");
                    t.setDaemon(true);
                    return t;
                },
                // 队列满时由调用方线程执行（背压控制）
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
