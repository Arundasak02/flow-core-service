package com.flow.core.service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async configuration with support for Java 21 Virtual Threads.
 *
 * When virtual threads are enabled, uses lightweight virtual threads for I/O-bound
 * operations like database calls, network requests, and queue operations.
 * Falls back to traditional thread pools when disabled.
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final FlowConfig flowConfig;

    // ==================== Executor Beans ====================

    /**
     * Executor for merge operations.
     * Uses virtual threads when enabled, otherwise falls back to thread pool.
     */
    @Bean(name = "mergeExecutor")
    public Executor mergeExecutor() {
        if (isVirtualThreadsEnabled()) {
            log.info("Initializing merge executor with virtual threads");
            return new VirtualThreadTaskExecutor("merge-");
        }
        log.info("Initializing merge executor with platform thread pool");
        return createPlatformThreadPool("merge-", 2, 4, 100);
    }

    /**
     * Executor for export operations (Neo4j, etc.).
     * Uses virtual threads when enabled, otherwise falls back to thread pool.
     */
    @Bean(name = "exportExecutor")
    public Executor exportExecutor() {
        if (isVirtualThreadsEnabled()) {
            log.info("Initializing export executor with virtual threads");
            return new VirtualThreadTaskExecutor("export-");
        }
        log.info("Initializing export executor with platform thread pool");
        return createPlatformThreadPool("export-", 1, 2, 50);
    }

    // ==================== Helper Methods ====================

    private boolean isVirtualThreadsEnabled() {
        return flowConfig.getFeatures().isVirtualThreadsEnabled();
    }

    private ThreadPoolTaskExecutor createPlatformThreadPool(String prefix, int coreSize,
                                                             int maxSize, int queueCapacity) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // ==================== Virtual Thread Executor ====================

    /**
     * Custom TaskExecutor that uses Java 21 virtual threads.
     * Virtual threads are extremely lightweight (~1KB vs ~1MB for platform threads)
     * and ideal for blocking I/O operations.
     */
    private static class VirtualThreadTaskExecutor implements TaskExecutor {

        private final ExecutorService executor;

        VirtualThreadTaskExecutor(String namePrefix) {
            this.executor = Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual()
                            .name(namePrefix, 0)
                            .factory()
            );
        }

        @Override
        public void execute(Runnable task) {
            executor.execute(task);
        }
    }
}
