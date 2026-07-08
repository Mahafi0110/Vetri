package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated thread pool for file processing jobs.
     * - corePoolSize: always-alive threads (handles normal load)
     * - maxPoolSize:  burst ceiling (never exceed, prevents OOM)
     * - queueCapacity: backlog before spinning up extra threads
     *
     * Tune these based on your Render instance RAM:
     *   Free (512MB)  → core=2, max=4,  queue=20
     *   Starter (1GB) → core=4, max=8,  queue=50
     *   Standard (2GB)→ core=6, max=12, queue=100
     */
    @Bean(name = "fileProcessingExecutor")
    public Executor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("vetri-file-");

        // When queue is full → caller runs the task (prevents silent drops)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
