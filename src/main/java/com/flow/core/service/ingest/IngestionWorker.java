package com.flow.core.service.ingest;

import com.flow.core.service.config.FlowConfig;
import com.flow.core.service.config.IngestionConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker service that processes items from the ingestion queue.
 *
 * Supports both Java 21 virtual threads and traditional platform threads.
 * Virtual threads are ideal for this use case because workers spend most time
 * blocked on queue.poll(), waiting for work items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionWorker {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final IngestionQueue queue;
    private final IngestionHandler<IngestionWorkItem.StaticGraphWorkItem> staticGraphHandler;
    private final IngestionHandler<IngestionWorkItem.RuntimeEventWorkItem> runtimeEventHandler;
    private final IngestionConfig ingestionConfig;
    private final FlowConfig flowConfig;

    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    // ==================== Lifecycle ====================

    @PostConstruct
    void start() {
        int workerCount = ingestionConfig.getWorker().getThreadCount();
        executorService = createExecutorService();
        running.set(true);
        startWorkers(workerCount);
        logStartup(workerCount);
    }

    @PreDestroy
    void stop() {
        running.set(false);
        shutdownExecutor();
        log.info("IngestionWorker stopped. Final active workers: {}", activeWorkers.get());
    }

    // ==================== Executor Management ====================

    private ExecutorService createExecutorService() {
        if (isVirtualThreadsEnabled()) {
            return createVirtualThreadExecutor();
        }
        return createPlatformThreadExecutor();
    }

    /**
     * Creates a virtual thread executor using Java 21's virtual threads.
     * Each task gets its own virtual thread (~1KB vs ~1MB for platform threads).
     */
    private ExecutorService createVirtualThreadExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("ingestion-vworker-", 0)
                        .factory()
        );
    }

    /**
     * Creates a traditional fixed thread pool with platform threads.
     */
    private ExecutorService createPlatformThreadExecutor() {
        int threadCount = ingestionConfig.getWorker().getThreadCount();
        return Executors.newFixedThreadPool(threadCount, this::createPlatformThread);
    }

    private Thread createPlatformThread(Runnable runnable) {
        var thread = new Thread(runnable);
        thread.setName("ingestion-worker-" + thread.threadId());
        thread.setDaemon(true);
        return thread;
    }

    private void startWorkers(int workerCount) {
        for (int i = 0; i < workerCount; i++) {
            executorService.submit(this::processLoop);
        }
    }

    private void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of ingestion workers");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    // ==================== Processing Loop ====================

    private void processLoop() {
        activeWorkers.incrementAndGet();
        var pollTimeoutMs = ingestionConfig.getTimeout().getPollMs();

        try {
            while (running.get()) {
                processNextItem(pollTimeoutMs);
            }
        } finally {
            activeWorkers.decrementAndGet();
        }
    }

    private void processNextItem(long pollTimeoutMs) {
        try {
            queue.dequeue(pollTimeoutMs)
                    .ifPresent(this::processWorkItem);
        } catch (Exception e) {
            log.error("Error in ingestion worker loop", e);
        }
    }

    // ==================== Work Item Dispatch ====================

    private void processWorkItem(IngestionWorkItem item) {
        try {
            dispatchToHandler(item);
        } catch (IngestionException e) {
            logIngestionError(item, e);
        } catch (Exception e) {
            logUnexpectedError(item, e);
        }
    }

    private void dispatchToHandler(IngestionWorkItem item) {
        switch (item) {
            case IngestionWorkItem.StaticGraphWorkItem staticItem ->
                    staticGraphHandler.handle(staticItem);
            case IngestionWorkItem.RuntimeEventWorkItem runtimeItem ->
                    runtimeEventHandler.handle(runtimeItem);
        }
    }

    // ==================== Monitoring ====================

    /**
     * Returns the current number of active workers.
     */
    public int getActiveWorkerCount() {
        return activeWorkers.get();
    }

    /**
     * Returns whether virtual threads are being used.
     */
    public boolean isUsingVirtualThreads() {
        return isVirtualThreadsEnabled();
    }

    // ==================== Helper Methods ====================

    private boolean isVirtualThreadsEnabled() {
        return flowConfig.getFeatures().isVirtualThreadsEnabled();
    }

    private void logStartup(int workerCount) {
        String threadType = isVirtualThreadsEnabled() ? "virtual" : "platform";
        log.info("IngestionWorker started with {} {} thread workers", workerCount, threadType);
    }

    private void logIngestionError(IngestionWorkItem item, IngestionException e) {
        log.error("Ingestion failed for {}: {} [{}]",
                item.getEntityId(), e.getMessage(), e.getErrorCode());
    }

    private void logUnexpectedError(IngestionWorkItem item, Exception e) {
        log.error("Unexpected error processing work item: {}", item.getEntityId(), e);
    }
}
