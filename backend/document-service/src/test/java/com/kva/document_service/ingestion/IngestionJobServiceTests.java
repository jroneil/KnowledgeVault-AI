package com.kva.document_service.ingestion;

import com.kva.document_service.ai.DocumentVersionProcessingClient;
import com.kva.document_service.ai.dto.DocumentVersionProcessingRequest;
import com.kva.document_service.ai.dto.DocumentVersionProcessingResponse;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.documents.DocumentVersion;
import com.kva.document_service.documents.DocumentVersionRepository;
import com.kva.document_service.ingestion.dto.IngestionJobResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionJobServiceTests {

    @Test
    void createPendingJobStoresConfiguredModels() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );
        ReflectionTestUtils.setField(service, "defaultEmbeddingModel", "nomic-embed-text");
        ReflectionTestUtils.setField(service, "defaultLlmModel", "llama3.1");

        IngestionJob saved = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .embeddingModel("nomic-embed-text")
                .llmModel("llama3.1")
                .chunkCount(0)
                .embeddingCount(0)
                .build();

        when(repository.findByDocumentIdAndVersionId(42L, 100L)).thenReturn(Optional.empty());
        when(repository.save(any(IngestionJob.class))).thenReturn(saved);

        IngestionJobResponse response = service.createPendingJobIfAbsent(42L, 100L);

        verify(repository).save(any(IngestionJob.class));
        assertEquals("nomic-embed-text", response.getEmbeddingModel());
        assertEquals("llama3.1", response.getLlmModel());
        assertEquals(IngestionJobStatus.PENDING, response.getStatus());
    }

    @Test
    void duplicateJobIsNotCreatedForSameDocumentAndVersion() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        IngestionJob existing = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .chunkCount(0)
                .embeddingCount(0)
                .build();

        when(repository.findByDocumentIdAndVersionId(42L, 100L)).thenReturn(Optional.of(existing));

        IngestionJobResponse response = service.createPendingJobIfAbsent(42L, 100L);

        verify(repository, never()).save(any(IngestionJob.class));
        assertEquals(55L, response.getId());
        assertEquals(42L, response.getDocumentId());
        assertEquals(100L, response.getVersionId());
    }

    @Test
    void processPendingJobStoresCountsAndMarksIndexedWhenAiClientSucceeds() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentExtractionRepository extractionRepository = mock(DocumentExtractionRepository.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository,
                extractionRepository,
                defaultOcrClient(),
                chunkRepository
        );

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .chunkCount(0)
                .embeddingCount(0)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(true)
                        .chunkCount(3)
                        .embeddingCount(3)
                        .warningMessages(List.of())
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);
        ArgumentCaptor<DocumentVersionProcessingRequest> requestCaptor =
                ArgumentCaptor.forClass(DocumentVersionProcessingRequest.class);
        ArgumentCaptor<DocumentExtraction> extractionCaptor = ArgumentCaptor.forClass(DocumentExtraction.class);
        ArgumentCaptor<List<DocumentChunk>> chunkCaptor = ArgumentCaptor.forClass(List.class);

        assertTrue(processed);
        verify(repository).claimPendingJob(any(), any(), any(), any(), any());
        verify(processingClient).processDocumentVersion(requestCaptor.capture());
        verify(extractionRepository).save(extractionCaptor.capture());
        verify(chunkRepository).saveAll(chunkCaptor.capture());
        verify(repository).save(jobCaptor.capture());
        assertEquals(55L, requestCaptor.getValue().getJobId());
        assertEquals(42L, requestCaptor.getValue().getDocumentId());
        assertEquals(100L, requestCaptor.getValue().getVersionId());
        assertEquals(fixturePath("version-one.txt"), requestCaptor.getValue().getStoragePath());
        assertEquals("TXT", extractionCaptor.getValue().getExtractorType());
        assertEquals(1, extractionCaptor.getValue().getPageCount());
        assertEquals(1, extractionCaptor.getValue().getPages().size());
        assertEquals(1, chunkCaptor.getValue().size());
        assertEquals(0, chunkCaptor.getValue().get(0).getChunkIndex());
        assertEquals(1, chunkCaptor.getValue().get(0).getSourcePageFrom());
        assertEquals(IngestionJobStatus.INDEXED, jobCaptor.getValue().getStatus());
        assertEquals(100, jobCaptor.getValue().getProgressPercent());
        assertEquals(1, jobCaptor.getValue().getChunkCount());
        assertEquals(1, jobCaptor.getValue().getEmbeddingCount());
    }

    @Test
    void processPendingJobDoesNotDoubleProcessWhenClaimFails() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        IngestionJob pendingJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();

        when(repository.findById(55L)).thenReturn(Optional.of(pendingJob));
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(0);

        boolean processed = service.processPendingJob(55L);

        assertFalse(processed);
        verify(processingClient, never()).processDocumentVersion(any());
        verify(repository, never()).save(any(IngestionJob.class));
    }

    @Test
    void requestCancellationCancelsPendingJobImmediately() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        IngestionJob pendingJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();

        when(repository.findById(55L)).thenReturn(Optional.of(pendingJob));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionJobResponse response = service.requestCancellation(55L);

        assertEquals(IngestionJobStatus.CANCELLED, response.getStatus());
        assertTrue(Boolean.TRUE.equals(response.getCancellationRequested()));
        verify(repository).save(any(IngestionJob.class));
    }

    @Test
    void processPendingJobCancelsBeforeClaimWhenCancellationAlreadyRequested() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        IngestionJob pendingJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(true)
                .build();

        when(repository.findById(55L)).thenReturn(Optional.of(pendingJob));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertFalse(processed);
        verify(repository, never()).claimPendingJob(any(), any(), any(), any(), any());
        verify(processingClient, never()).processDocumentVersion(any());
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.CANCELLED, jobCaptor.getValue().getStatus());
    }

    @Test
    void processPendingJobCancelsAfterClaimWhenCancellationWasRequestedDuringProcessing() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        IngestionJob beforeClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();
        IngestionJob afterClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(true)
                .build();

        when(repository.findById(55L)).thenReturn(Optional.of(beforeClaim), Optional.of(afterClaim));
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(repository).claimPendingJob(any(), any(), any(), any(), any());
        verify(processingClient, never()).processDocumentVersion(any());
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.CANCELLED, jobCaptor.getValue().getStatus());
    }

    @Test
    void processPendingJobDoesNotMarkCancelledProcessingJobIndexed() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        IngestionJob beforeClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();
        IngestionJob afterClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(false)
                .build();
        IngestionJob beforeCompletion = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(true)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(beforeClaim),
                Optional.of(afterClaim),
                Optional.of(beforeCompletion)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(true)
                        .chunkCount(3)
                        .embeddingCount(3)
                        .warningMessages(List.of())
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(processingClient, never()).processDocumentVersion(any());
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.CANCELLED, jobCaptor.getValue().getStatus());
    }

    @Test
    void processPendingJobCancelsBeforeChunkPersistenceWhenCancellationRequestedAfterExtraction() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentExtractionRepository extractionRepository = mock(DocumentExtractionRepository.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository,
                extractionRepository,
                defaultOcrClient(),
                chunkRepository
        );

        IngestionJob beforeClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();
        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(false)
                .build();
        IngestionJob beforeChunking = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(true)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(beforeClaim),
                Optional.of(claimedJob),
                Optional.of(beforeChunking)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(extractionRepository.save(any(DocumentExtraction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(extractionRepository).save(any(DocumentExtraction.class));
        verify(chunkRepository, never()).saveAll(any());
        verify(processingClient, never()).processDocumentVersion(any());
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.CANCELLED, jobCaptor.getValue().getStatus());
    }

    @Test
    void processPendingJobMarksFailedAndStoresErrorWhenAiClientFails() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .chunkCount(0)
                .embeddingCount(0)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(false)
                        .errorCode("PLACEHOLDER_FAILURE")
                        .errorMessage("placeholder processing failed")
                        .warningMessages(List.of())
                        .retryable(false)
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.FAILED, jobCaptor.getValue().getStatus());
        assertEquals("PLACEHOLDER_FAILURE", jobCaptor.getValue().getErrorCode());
        assertEquals("placeholder processing failed", jobCaptor.getValue().getErrorMessage());
    }

    @Test
    void processPendingJobSchedulesRetryableFailureWithBackoff() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .chunkCount(0)
                .embeddingCount(0)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(false)
                        .errorCode("AI_TIMEOUT")
                        .errorMessage("timed out")
                        .retryable(true)
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.PENDING, jobCaptor.getValue().getStatus());
        assertEquals(true, jobCaptor.getValue().getRetryable());
        assertEquals("AI_TIMEOUT", jobCaptor.getValue().getLastErrorCode());
        assertEquals("timed out", jobCaptor.getValue().getLastErrorMessage());
        assertEquals(null, jobCaptor.getValue().getErrorCode());
        assertTrue(jobCaptor.getValue().getNextAttemptAt().isAfter(LocalDateTime.now().minusSeconds(1)));
    }

    @Test
    void processPendingJobMarksRetryableFailureAsFailedAfterMaxAttempts() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionWorkerProperties workerProperties = newWorkerProperties();
        workerProperties.setMaxAttempts(3);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                workerProperties
        );

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(3)
                .chunkCount(0)
                .embeddingCount(0)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(false)
                        .errorCode("AI_TIMEOUT")
                        .errorMessage("timed out")
                        .retryable(true)
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.FAILED, jobCaptor.getValue().getStatus());
        assertEquals("RETRY_LIMIT_EXCEEDED", jobCaptor.getValue().getErrorCode());
        assertEquals(false, jobCaptor.getValue().getRetryable());
    }

    @Test
    void processPendingJobFailsUnsupportedFileTypeWithoutCallingAiClient() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        IngestionJob beforeClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();
        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(false)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(fixturePath("version-one.txt"))
                .fileName("sheet.xlsx")
                .mimeType("application/vnd.ms-excel")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(beforeClaim),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);
        ArgumentCaptor<IngestionWarning> warningCaptor = ArgumentCaptor.forClass(IngestionWarning.class);

        assertTrue(processed);
        verify(processingClient, never()).processDocumentVersion(any());
        verify(warningRepository).save(warningCaptor.capture());
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.FAILED, jobCaptor.getValue().getStatus());
        assertEquals(DocumentProcessingValidationService.UNSUPPORTED_FILE_TYPE_ERROR_CODE,
                jobCaptor.getValue().getErrorCode());
        assertEquals(DocumentProcessingValidationService.UNSUPPORTED_FILE_TYPE_ERROR_CODE,
                warningCaptor.getValue().getWarningCode());
        assertEquals(IngestionWarningSeverity.ERROR, warningCaptor.getValue().getSeverity());
    }

    @Test
    void processPendingJobFailsEncryptedPdfWithoutCallingAiClient() throws IOException {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        Path encryptedPdf = Files.createTempFile("kv-encrypted-", ".pdf");
        Files.writeString(encryptedPdf, "%PDF-1.7\n1 0 obj\n<< /Encrypt 2 0 R >>\n", StandardCharsets.ISO_8859_1);

        IngestionJob beforeClaim = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .cancellationRequested(false)
                .build();
        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .cancellationRequested(false)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(encryptedPdf.toString())
                .fileName("secret.pdf")
                .mimeType("application/pdf")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(beforeClaim),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try {
            boolean processed = service.processPendingJob(55L);
            ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

            assertTrue(processed);
            verify(processingClient, never()).processDocumentVersion(any());
            verify(warningRepository).save(any(IngestionWarning.class));
            verify(repository).save(jobCaptor.capture());
            assertEquals(IngestionJobStatus.FAILED, jobCaptor.getValue().getStatus());
            assertEquals(DocumentProcessingValidationService.ENCRYPTED_DOCUMENT_ERROR_CODE,
                    jobCaptor.getValue().getErrorCode());
        } finally {
            Files.deleteIfExists(encryptedPdf);
        }
    }

    @Test
    void processPendingJobPersistsBlankPageWarningsDuringPdfExtraction() throws IOException {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        Path pdfPath = createPdfWithBlankSecondPage();

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .chunkCount(0)
                .embeddingCount(0)
                .cancellationRequested(false)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(pdfPath.toString())
                .fileName("manual.pdf")
                .mimeType("application/pdf")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(true)
                        .chunkCount(0)
                        .embeddingCount(0)
                        .warningMessages(List.of())
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try {
            boolean processed = service.processPendingJob(55L);

            assertTrue(processed);
            verify(warningRepository).saveAll(any());
            verify(repository).save(any(IngestionJob.class));
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void processPendingJobPersistsOcrEnhancedPageContent() throws IOException {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        DocumentExtractionRepository extractionRepository = mock(DocumentExtractionRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        when(ocrClient.performOcr(any())).thenReturn(DocumentPageOcrResult.builder()
                .success(true)
                .text("Recovered OCR content for blank page")
                .confidence(0.93d)
                .build());
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository,
                extractionRepository,
                ocrClient
        );

        Path pdfPath = createPdfWithBlankSecondPage();

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .chunkCount(0)
                .embeddingCount(0)
                .cancellationRequested(false)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(pdfPath.toString())
                .fileName("manual.pdf")
                .mimeType("application/pdf")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(processingClient.processDocumentVersion(any())).thenReturn(
                DocumentVersionProcessingResponse.builder()
                        .success(true)
                        .chunkCount(0)
                        .embeddingCount(0)
                        .warningMessages(List.of())
                        .build()
        );
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(extractionRepository.save(any(DocumentExtraction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try {
            boolean processed = service.processPendingJob(55L);
            ArgumentCaptor<DocumentExtraction> extractionCaptor = ArgumentCaptor.forClass(DocumentExtraction.class);

            assertTrue(processed);
            verify(extractionRepository).save(extractionCaptor.capture());
            assertEquals(2, extractionCaptor.getValue().getPages().size());
            DocumentExtractionPage secondPage = extractionCaptor.getValue().getPages().get(1);
            assertTrue(Boolean.TRUE.equals(secondPage.getOcrApplied()));
            assertEquals("", secondPage.getOriginalText());
            assertEquals("Recovered OCR content for blank page", secondPage.getOcrText());
            assertEquals("Recovered OCR content for blank page", secondPage.getExtractedText());
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void processPendingJobMarksFailedWhenTextExtractionFails() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        IngestionJob claimedJob = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(1)
                .chunkCount(0)
                .embeddingCount(0)
                .cancellationRequested(false)
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .filePath(Path.of("does-not-exist.txt").toAbsolutePath().toString())
                .fileName("manual.txt")
                .mimeType("text/plain")
                .build();

        when(repository.findById(55L)).thenReturn(
                Optional.of(claimedJob),
                Optional.of(claimedJob),
                Optional.of(claimedJob)
        );
        when(repository.claimPendingJob(any(), any(), any(), any(), any())).thenReturn(1);
        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = service.processPendingJob(55L);
        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);

        assertTrue(processed);
        verify(processingClient, never()).processDocumentVersion(any());
        verify(warningRepository).save(any(IngestionWarning.class));
        verify(repository).save(jobCaptor.capture());
        assertEquals(IngestionJobStatus.FAILED, jobCaptor.getValue().getStatus());
        assertEquals(DocumentTextExtractionService.EXTRACTION_FAILED_ERROR_CODE, jobCaptor.getValue().getErrorCode());
    }

    @Test
    void getJobIncludesPersistedWarnings() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        IngestionWarningRepository warningRepository = mock(IngestionWarningRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                warningRepository
        );

        IngestionJob job = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.FAILED)
                .progressPercent(100)
                .attemptCount(1)
                .build();
        IngestionWarning warning = IngestionWarning.builder()
                .id(9L)
                .ingestionJobId(55L)
                .documentId(42L)
                .versionId(100L)
                .warningCode("UNSUPPORTED_FILE_TYPE")
                .warningMessage("Unsupported document type for ingestion")
                .severity(IngestionWarningSeverity.ERROR)
                .build();

        when(repository.findById(55L)).thenReturn(Optional.of(job));
        when(warningRepository.findByIngestionJobIdOrderByCreatedAtAscIdAsc(55L)).thenReturn(List.of(warning));

        IngestionJobResponse response = service.getJob(55L);

        assertEquals(1, response.getWarnings().size());
        assertEquals("UNSUPPORTED_FILE_TYPE", response.getWarnings().get(0).getWarningCode());
        assertEquals(IngestionWarningSeverity.ERROR, response.getWarnings().get(0).getSeverity());
    }

    @Test
    void recoverStaleProcessingJobsRequeuesStaleJobs() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        when(repository.requeueStaleProcessingJobs(any(), any(), any(), any(), any())).thenReturn(2);

        int recovered = service.recoverStaleProcessingJobs(Duration.ofMinutes(10));

        assertEquals(2, recovered);
        verify(repository).requeueStaleProcessingJobs(any(), any(), any(), any(), any());
    }

    @Test
    void findPendingJobIdsHonorsBatchLimit() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        when(repository.findReadyPendingJobIds(any(), any(), any())).thenReturn(List.of(1L, 2L));

        List<Long> pending = service.findPendingJobIds(2);

        assertEquals(List.of(1L, 2L), pending);
        verify(repository).findReadyPendingJobIds(any(), any(), any());
    }

    @Test
    void requestReindexCreatesPendingJobWhenNoJobExists() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .versionNumber(2)
                .build();
        IngestionJob saved = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .chunkCount(0)
                .embeddingCount(0)
                .build();

        when(documentRepository.existsById(42L)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(42L, 2)).thenReturn(Optional.of(version));
        when(repository.findByDocumentIdAndVersionId(42L, 100L)).thenReturn(Optional.empty());
        when(repository.save(any(IngestionJob.class))).thenReturn(saved);

        IngestionJobResponse response = service.requestReindex(42L, 2);

        assertEquals(55L, response.getId());
        assertEquals(IngestionJobStatus.PENDING, response.getStatus());
        verify(repository).save(any(IngestionJob.class));
    }

    @Test
    void requestReindexResetsTerminalJobToPending() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .versionNumber(2)
                .build();
        IngestionJob existing = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.FAILED)
                .progressPercent(100)
                .attemptCount(2)
                .chunkCount(7)
                .embeddingCount(7)
                .errorCode("AI_PROCESSING_ERROR")
                .errorMessage("failed")
                .build();

        when(documentRepository.existsById(42L)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(42L, 2)).thenReturn(Optional.of(version));
        when(repository.findByDocumentIdAndVersionId(42L, 100L)).thenReturn(Optional.of(existing));
        when(repository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionJobResponse response = service.requestReindex(42L, 2);

        assertEquals(IngestionJobStatus.PENDING, response.getStatus());
        assertEquals(0, response.getProgressPercent());
        assertEquals(0, response.getChunkCount());
        assertEquals(0, response.getEmbeddingCount());
        assertEquals(null, response.getErrorCode());
        assertEquals(null, response.getErrorMessage());
    }

    @Test
    void requestReindexDoesNotResetPendingOrProcessingJob() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository documentVersionRepository = mock(DocumentVersionRepository.class);
        DocumentVersionProcessingClient processingClient = mock(DocumentVersionProcessingClient.class);
        IngestionJobService service = newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient
        );

        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .versionNumber(2)
                .build();
        IngestionJob existing = IngestionJob.builder()
                .id(55L)
                .documentId(42L)
                .versionId(100L)
                .status(IngestionJobStatus.PROCESSING)
                .progressPercent(10)
                .attemptCount(2)
                .chunkCount(0)
                .embeddingCount(0)
                .build();

        when(documentRepository.existsById(42L)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(42L, 2)).thenReturn(Optional.of(version));
        when(repository.findByDocumentIdAndVersionId(42L, 100L)).thenReturn(Optional.of(existing));

        IngestionJobResponse response = service.requestReindex(42L, 2);

        assertEquals(55L, response.getId());
        assertEquals(IngestionJobStatus.PROCESSING, response.getStatus());
        verify(repository, never()).save(any(IngestionJob.class));
    }

    private IngestionWorkerProperties newWorkerProperties() {
        IngestionWorkerProperties properties = new IngestionWorkerProperties();
        properties.setMaxAttempts(3);
        properties.setRetryInitialDelay(Duration.ofSeconds(30));
        properties.setRetryBackoffMultiplier(2.0d);
        properties.setRetryMaxDelay(Duration.ofMinutes(15));
        return properties;
    }

    private IngestionJobService newService(IngestionJobRepository repository,
                                           DocumentRepository documentRepository,
                                           DocumentVersionRepository documentVersionRepository,
                                           DocumentVersionProcessingClient processingClient) {
        return newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                newWorkerProperties(),
                mock(IngestionWarningRepository.class)
        );
    }

    private IngestionJobService newService(IngestionJobRepository repository,
                                           DocumentRepository documentRepository,
                                           DocumentVersionRepository documentVersionRepository,
                                           DocumentVersionProcessingClient processingClient,
                                           IngestionWorkerProperties workerProperties) {
        return newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                workerProperties,
                mock(IngestionWarningRepository.class)
        );
    }

    private IngestionJobService newService(IngestionJobRepository repository,
                                           DocumentRepository documentRepository,
                                           DocumentVersionRepository documentVersionRepository,
                                           DocumentVersionProcessingClient processingClient,
                                           IngestionWorkerProperties workerProperties,
                                           IngestionWarningRepository warningRepository) {
        return newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                workerProperties,
                warningRepository,
                mock(DocumentExtractionRepository.class)
        );
    }

    private IngestionJobService newService(IngestionJobRepository repository,
                                           DocumentRepository documentRepository,
                                           DocumentVersionRepository documentVersionRepository,
                                           DocumentVersionProcessingClient processingClient,
                                           IngestionWorkerProperties workerProperties,
                                           IngestionWarningRepository warningRepository,
                                           DocumentExtractionRepository extractionRepository) {
        return newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                workerProperties,
                warningRepository,
                extractionRepository,
                defaultOcrClient(),
                mock(DocumentChunkRepository.class)
        );
    }

    private IngestionJobService newService(IngestionJobRepository repository,
                                           DocumentRepository documentRepository,
                                           DocumentVersionRepository documentVersionRepository,
                                           DocumentVersionProcessingClient processingClient,
                                           IngestionWorkerProperties workerProperties,
                                           IngestionWarningRepository warningRepository,
                                           DocumentExtractionRepository extractionRepository,
                                           DocumentPageOcrClient ocrClient) {
        return newService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                workerProperties,
                warningRepository,
                extractionRepository,
                ocrClient,
                mock(DocumentChunkRepository.class)
        );
    }

    private IngestionJobService newService(IngestionJobRepository repository,
                                           DocumentRepository documentRepository,
                                           DocumentVersionRepository documentVersionRepository,
                                           DocumentVersionProcessingClient processingClient,
                                           IngestionWorkerProperties workerProperties,
                                           IngestionWarningRepository warningRepository,
                                           DocumentExtractionRepository extractionRepository,
                                           DocumentPageOcrClient ocrClient,
                                           DocumentChunkRepository chunkRepository) {
        when(warningRepository.findByIngestionJobIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());
        when(warningRepository.findByIngestionJobIdInOrderByIngestionJobIdAscCreatedAtAscIdAsc(any())).thenReturn(List.of());
        when(warningRepository.findByVersionIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());
        when(extractionRepository.save(any(DocumentExtraction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionOcrProperties ocrProperties = new IngestionOcrProperties();
        ocrProperties.setEnabled(true);
        ocrProperties.setLowTextThreshold(20);
        ocrProperties.setRenderDpi(72);
        ocrProperties.setImageFormat("png");
        ocrProperties.setLanguages(List.of("eng"));

        IngestionChunkingProperties chunkingProperties = new IngestionChunkingProperties();
        chunkingProperties.setEnabled(true);
        chunkingProperties.setChunkSize(1000);
        chunkingProperties.setOverlap(200);

        IngestionEmbeddingProperties embeddingProperties = new IngestionEmbeddingProperties();
        embeddingProperties.setEnabled(true);
        embeddingProperties.setProvider("ai-service");
        embeddingProperties.setModelName("nomic-embed-text");
        embeddingProperties.setModelVersion("test-v1");
        embeddingProperties.setDimension(768);
        embeddingProperties.setBatchSize(16);
        embeddingProperties.setTimeoutMs(30_000);

        DocumentEmbeddingRepository embeddingRepository = mock(DocumentEmbeddingRepository.class);

        return new IngestionJobService(
                repository,
                documentRepository,
                documentVersionRepository,
                processingClient,
                workerProperties,
                new IngestionWarningService(warningRepository),
                new DocumentProcessingValidationService(),
                new DocumentTextExtractionService(ocrClient, ocrProperties),
                new DocumentExtractionService(extractionRepository),
                new DocumentChunkingService(chunkingProperties),
                new DocumentChunkService(chunkRepository),
                new DocumentEmbeddingService(defaultEmbeddingClient(), embeddingRepository, embeddingProperties)
        );
    }

    private DocumentPageOcrClient defaultOcrClient() {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        when(ocrClient.performOcr(any())).thenReturn(DocumentPageOcrResult.builder()
                .success(false)
                .text("")
                .confidence(0.0d)
                .errorMessage("OCR unavailable in unit test")
                .build());
        return ocrClient;
    }

    private DocumentEmbeddingClient defaultEmbeddingClient() {
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        when(embeddingClient.generateEmbeddings(any())).thenAnswer(invocation -> {
            GenerateEmbeddingsBatchRequest request = invocation.getArgument(0);
            List<List<Double>> embeddings = request.getTexts().stream()
                    .map(text -> java.util.Collections.nCopies(768, 0.01d))
                    .map(List::copyOf)
                    .toList();

            return EmbeddingBatchResult.builder()
                    .success(true)
                    .retryable(false)
                    .embeddings(embeddings)
                    .provider("ai-service")
                    .modelName("nomic-embed-text")
                    .modelVersion("test-v1")
                    .build();
        });
        return embeddingClient;
    }

    private String fixturePath(String filename) {
        return Path.of("src", "test", "resources", "fixtures", filename).toAbsolutePath().toString();
    }

    private Path createPdfWithBlankSecondPage() throws IOException {
        Path pdfPath = Files.createTempFile("kv-extract-", ".pdf");

        try (PDDocument document = new PDDocument()) {
            PDPage firstPage = new PDPage();
            document.addPage(firstPage);
            document.addPage(new PDPage());

            try (PDPageContentStream contentStream = new PDPageContentStream(document, firstPage)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("KnowledgeVault PDF extraction test page.");
                contentStream.endText();
            }

            document.save(pdfPath.toFile());
        }

        return pdfPath;
    }
}
