package com.flow.core.service.runtime;

import com.flow.core.service.engine.MergeEngineAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;

/**
 * Periodically checks for traces that haven't received events recently
 * and marks them as complete to trigger the merge pipeline.
 *
 * <p>This is necessary because flow-runtime-agent does not send an explicit
 * "trace complete" signal — traces are considered done when no new events
 * arrive for {@code flow.retention.trace.idle-timeout-ms} milliseconds.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceCompletionScheduler {

    private final RuntimeTraceBuffer traceBuffer;
    private final MergeEngineAdapter mergeEngineAdapter;

    /**
     * How long (ms) a trace must be idle before it is auto-completed.
     * Default: 3000 ms (3 seconds).
     */
    @Value("${flow.retention.trace.idle-timeout-ms:3000}")
    private long idleTimeoutMs;

    /**
     * Every 5 seconds, check for idle incomplete traces and trigger their merge.
     */
    @Scheduled(fixedDelayString = "${flow.retention.trace.completion-check-interval-ms:5000}")
    public void checkIdleTraces() {
        long cutoff = Instant.now().toEpochMilli() - idleTimeoutMs;
        Collection<RuntimeTraceBuffer.RuntimeTrace> idleTraces = traceBuffer.getIdleIncompleteTraces(cutoff);

        if (idleTraces.isEmpty()) return;

        log.debug("TraceCompletionScheduler: found {} idle traces to complete", idleTraces.size());

        for (RuntimeTraceBuffer.RuntimeTrace trace : idleTraces) {
            try {
                log.info("Auto-completing idle trace: traceId={}, graphId={}", trace.traceId(), trace.graphId());
                traceBuffer.markComplete(trace.traceId());
                mergeEngineAdapter.mergeTrace(trace.traceId(), trace.graphId());
            } catch (Exception e) {
                log.error("Failed to auto-complete trace: traceId={}", trace.traceId(), e);
            }
        }
    }
}

