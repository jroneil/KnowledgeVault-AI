package com.kva.document_service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionJobWorker {

    private final IngestionJobService ingestionJobService;
    private final IngestionWorkerProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            fixedDelayString = "${ingestion.worker.fixed-delay:PT30S}",
            initialDelayString = "${ingestion.worker.initial-delay:PT10S}"
    )
    public void pollAndProcess() {
        runOnce();
    }

    void runOnce() {
        if (!properties.isEnabled()) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping ingestion worker run because a previous run is still active");
            return;
        }

        try {
            int recovered = ingestionJobService.recoverStaleProcessingJobs(properties.getStaleThreshold());
            if (recovered > 0) {
                log.info("Recovered {} stale ingestion job(s)", recovered);
            }

            List<Long> pendingJobIds = ingestionJobService.findPendingJobIds(properties.getBatchSize());
            for (Long jobId : pendingJobIds) {
                boolean processed = ingestionJobService.processPendingJob(jobId);
                if (processed) {
                    log.info("Processed ingestion job {}", jobId);
                }
            }
        } finally {
            running.set(false);
        }
    }
}
