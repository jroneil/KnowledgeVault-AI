package com.kva.document_service.ingestion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionJobWorkerTests {

    @Test
    void workerProcessesPendingJobsUpToConfiguredBatchSize() {
        IngestionJobService service = mock(IngestionJobService.class);
        IngestionWorkerProperties properties = new IngestionWorkerProperties();
        properties.setEnabled(true);
        properties.setBatchSize(2);
        properties.setStaleThreshold(Duration.ofMinutes(10));
        IngestionJobWorker worker = new IngestionJobWorker(service, properties);

        when(service.findPendingJobIds(2)).thenReturn(List.of(11L, 12L));
        when(service.processPendingJob(11L)).thenReturn(true);
        when(service.processPendingJob(12L)).thenReturn(true);

        worker.runOnce();

        verify(service).recoverStaleProcessingJobs(Duration.ofMinutes(10));
        verify(service).findPendingJobIds(2);
        verify(service).processPendingJob(11L);
        verify(service).processPendingJob(12L);
    }

    @Test
    void workerSkipsRunWhenDisabled() {
        IngestionJobService service = mock(IngestionJobService.class);
        IngestionWorkerProperties properties = new IngestionWorkerProperties();
        properties.setEnabled(false);
        IngestionJobWorker worker = new IngestionJobWorker(service, properties);

        worker.runOnce();

        verify(service, never()).recoverStaleProcessingJobs(any());
        verify(service, never()).findPendingJobIds(anyInt());
    }

    @Test
    void workerDoesNotOverlapWithAnotherRun() throws Exception {
        IngestionJobService service = mock(IngestionJobService.class);
        IngestionWorkerProperties properties = new IngestionWorkerProperties();
        properties.setEnabled(true);
        properties.setBatchSize(1);
        properties.setStaleThreshold(Duration.ofMinutes(10));
        IngestionJobWorker worker = new IngestionJobWorker(service, properties);

        CountDownLatch enteredProcess = new CountDownLatch(1);
        CountDownLatch releaseProcess = new CountDownLatch(1);

        when(service.findPendingJobIds(1)).thenReturn(List.of(99L));
        when(service.processPendingJob(99L)).thenAnswer(invocation -> {
            enteredProcess.countDown();
            releaseProcess.await(5, TimeUnit.SECONDS);
            return true;
        });

        Thread firstRun = new Thread(worker::runOnce);
        firstRun.start();

        assertTrue(enteredProcess.await(5, TimeUnit.SECONDS));
        worker.runOnce();
        releaseProcess.countDown();
        firstRun.join(5000);

        verify(service, times(1)).findPendingJobIds(1);
        verify(service, times(1)).processPendingJob(99L);
    }
}
