package com.kva.document_service.ingestion;

import com.kva.document_service.ai.DocumentVersionProcessingClient;
import com.kva.document_service.ai.dto.DocumentVersionProcessingRequest;
import com.kva.document_service.ai.dto.DocumentVersionProcessingResponse;
import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.documents.DocumentVersion;
import com.kva.document_service.documents.DocumentVersionRepository;
import com.kva.document_service.ingestion.dto.IngestionJobResponse;
import com.kva.document_service.ingestion.dto.IngestionWarningResponse;
import com.kva.document_service.ingestion.dto.UpdateIngestionJobStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionJobService {

    private static final int PROCESSING_PROGRESS_PERCENT = 10;
    private static final int INDEXED_PROGRESS_PERCENT = 100;
    private static final String PROCESSING_ERROR_CODE = "AI_PROCESSING_ERROR";
    private static final String RETRY_LIMIT_EXCEEDED_ERROR_CODE = "RETRY_LIMIT_EXCEEDED";
    private static final String STALE_ERROR_CODE = "STALE_PROCESSING_RECOVERED";
    private static final String STALE_ERROR_MESSAGE = "Recovered stale processing job for retry";

    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentVersionProcessingClient documentVersionProcessingClient;
    private final IngestionWorkerProperties ingestionWorkerProperties;
    private final IngestionWarningService ingestionWarningService;
    private final DocumentProcessingValidationService documentProcessingValidationService;
    private final DocumentTextExtractionService documentTextExtractionService;
    private final DocumentExtractionService documentExtractionService;
    private final DocumentChunkingService documentChunkingService;
    private final DocumentChunkService documentChunkService;
    private final DocumentEmbeddingService documentEmbeddingService;

    @Value("${ai-service.embedding-model:}")
    private String defaultEmbeddingModel;

    @Value("${ai-service.llm-model:}")
    private String defaultLlmModel;

    @Transactional(readOnly = true)
    public IngestionJobResponse getJob(Long jobId) {
        IngestionJob job = findJobEntity(jobId);
        return IngestionJobResponse.fromEntity(job, ingestionWarningService.listWarningsForJob(jobId));
    }

    @Transactional(readOnly = true)
    public List<IngestionJobResponse> listJobsForDocument(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException("Document not found with id: " + documentId);
        }

        List<IngestionJob> jobs = ingestionJobRepository.findByDocumentIdOrderByCreatedAtDescIdDesc(documentId);
        List<Long> jobIds = jobs.stream().map(IngestionJob::getId).toList();
        var warningsByJobId = ingestionWarningService.listWarningsByJobIds(jobIds);

        return jobs.stream()
                .map(job -> IngestionJobResponse.fromEntity(
                        job,
                        warningsByJobId.getOrDefault(job.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public IngestionJobResponse createPendingJobIfAbsent(Long documentId, Long versionId) {
        IngestionJob existing = ingestionJobRepository.findByDocumentIdAndVersionId(documentId, versionId)
                .orElse(null);
        if (existing != null) {
            return IngestionJobResponse.fromEntity(existing, ingestionWarningService.listWarningsForJob(existing.getId()));
        }

        IngestionJob job = IngestionJob.builder()
                .documentId(documentId)
                .versionId(versionId)
                .status(IngestionJobStatus.PENDING)
                .progressPercent(0)
                .attemptCount(0)
                .embeddingModel(normalize(defaultEmbeddingModel))
                .llmModel(normalize(defaultLlmModel))
                .chunkCount(0)
                .embeddingCount(0)
                .nextAttemptAt(null)
                .retryable(false)
                .lastErrorCode(null)
                .lastErrorMessage(null)
                .cancellationRequested(false)
                .errorCode(null)
                .errorMessage(null)
                .startedAt(null)
                .completedAt(null)
                .build();

        return IngestionJobResponse.fromEntity(ingestionJobRepository.save(job), List.of());
    }

    @Transactional
    public IngestionJobResponse requestReindex(Long documentId, Integer versionNumber) {
        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException("Document not found with id: " + documentId);
        }

        DocumentVersion version = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber + " not found for document: " + documentId
                ));

        IngestionJob existing = ingestionJobRepository
                .findByDocumentIdAndVersionId(documentId, version.getId())
                .orElse(null);
        if (existing == null) {
            return createPendingJobIfAbsent(documentId, version.getId());
        }

        if (existing.getStatus() == IngestionJobStatus.PENDING
                || existing.getStatus() == IngestionJobStatus.PROCESSING) {
            return IngestionJobResponse.fromEntity(existing, ingestionWarningService.listWarningsForJob(existing.getId()));
        }

        existing.setStatus(IngestionJobStatus.PENDING);
        existing.setProgressPercent(0);
        existing.setStartedAt(null);
        existing.setCompletedAt(null);
        existing.setNextAttemptAt(null);
        existing.setRetryable(false);
        existing.setLastErrorCode(null);
        existing.setLastErrorMessage(null);
        existing.setCancellationRequested(false);
        existing.setErrorCode(null);
        existing.setErrorMessage(null);
        existing.setChunkCount(0);
        existing.setEmbeddingCount(0);
        ingestionWarningService.clearWarningsForJob(existing.getId());

        return IngestionJobResponse.fromEntity(ingestionJobRepository.save(existing), List.of());
    }

    @Transactional
    public IngestionJobResponse requestCancellation(Long jobId) {
        IngestionJob job = findJobEntity(jobId);
        job.setCancellationRequested(true);

        if (job.getStatus() == IngestionJobStatus.PENDING) {
            cancelJob(job);
        }

        return IngestionJobResponse.fromEntity(
                ingestionJobRepository.save(job),
                ingestionWarningService.listWarningsForJob(job.getId())
        );
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingJobIds(int limit) {
        return ingestionJobRepository.findReadyPendingJobIds(
                IngestionJobStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public boolean processPendingJob(Long jobId) {
        IngestionJob beforeClaim = findJobEntity(jobId);
        if (Boolean.TRUE.equals(beforeClaim.getCancellationRequested())) {
            cancelJob(beforeClaim);
            ingestionJobRepository.save(beforeClaim);
            return false;
        }

        LocalDateTime startedAt = LocalDateTime.now();
        int claimed = ingestionJobRepository.claimPendingJob(
                jobId,
                IngestionJobStatus.PENDING,
                IngestionJobStatus.PROCESSING,
                PROCESSING_PROGRESS_PERCENT,
                startedAt
        );
        if (claimed == 0) {
            return false;
        }

        IngestionJob claimedJob = findJobEntity(jobId);
        if (Boolean.TRUE.equals(claimedJob.getCancellationRequested())) {
            cancelJob(claimedJob);
            ingestionJobRepository.save(claimedJob);
            return true;
        }

        ingestionWarningService.clearWarningsForJob(jobId);

        try {
            DocumentVersion version = findDocumentVersion(claimedJob.getVersionId());
            DocumentProcessingValidationResult validationResult = documentProcessingValidationService.validate(version);
            if (!validationResult.isValid()) {
                ingestionWarningService.addError(
                        claimedJob.getId(),
                        claimedJob.getDocumentId(),
                        claimedJob.getVersionId(),
                        validationResult.getErrorCode(),
                        validationResult.getErrorMessage()
                );
                markJobFailed(jobId, validationResult.getErrorCode(), validationResult.getErrorMessage());
                return true;
            }

            ExtractedDocument extractedDocument = documentTextExtractionService.extract(version);
            persistExtractionWarnings(claimedJob, extractedDocument);
            DocumentExtraction extraction = documentExtractionService.replaceExtraction(
                    claimedJob.getId(),
                    claimedJob.getDocumentId(),
                    claimedJob.getVersionId(),
                    extractedDocument
            );

            IngestionJob beforeChunking = findJobEntity(jobId);
            if (Boolean.TRUE.equals(beforeChunking.getCancellationRequested())) {
                cancelJob(beforeChunking);
                ingestionJobRepository.save(beforeChunking);
                return true;
            }

            List<DocumentChunkDraft> chunkDrafts = documentChunkingService.createChunks(extraction);
            List<DocumentChunk> persistedChunks = documentChunkService.replaceChunks(
                    claimedJob.getId(),
                    claimedJob.getDocumentId(),
                    claimedJob.getVersionId(),
                    chunkDrafts
            );

            IngestionJob beforeEmbeddings = findJobEntity(jobId);
            if (Boolean.TRUE.equals(beforeEmbeddings.getCancellationRequested())) {
                cancelJob(beforeEmbeddings);
                ingestionJobRepository.save(beforeEmbeddings);
                return true;
            }

            EmbeddingRunResult embeddingRunResult = documentEmbeddingService.generateAndPersistEmbeddings(persistedChunks);
            if (!embeddingRunResult.isSuccess()) {
                if (embeddingRunResult.isRetryable()) {
                    scheduleRetryOrFail(jobId, embeddingRunResult.getErrorCode(), embeddingRunResult.getErrorMessage());
                } else {
                    markJobFailed(jobId, embeddingRunResult.getErrorCode(), embeddingRunResult.getErrorMessage());
                }
                return true;
            }

            DocumentVersionProcessingRequest request = buildProcessingRequest(claimedJob, version);
            DocumentVersionProcessingResponse response =
                    documentVersionProcessingClient.processDocumentVersion(request);
            persistResponseWarnings(claimedJob, response);

            IngestionJob beforeCompletion = findJobEntity(jobId);
            if (Boolean.TRUE.equals(beforeCompletion.getCancellationRequested())) {
                cancelJob(beforeCompletion);
                ingestionJobRepository.save(beforeCompletion);
                return true;
            }

            if (response.isSuccess()) {
                markJobIndexed(jobId, withCounts(response, chunkDrafts.size(), embeddingRunResult.getEmbeddingCount()));
            } else if (Boolean.TRUE.equals(response.getRetryable())) {
                scheduleRetryOrFail(jobId, response.getErrorCode(), response.getErrorMessage());
            } else {
                markJobFailed(jobId, response.getErrorCode(), response.getErrorMessage());
            }
        } catch (DocumentTextExtractionException exception) {
            ingestionWarningService.addError(
                    claimedJob.getId(),
                    claimedJob.getDocumentId(),
                    claimedJob.getVersionId(),
                    exception.getErrorCode(),
                    exception.getMessage()
            );
            markJobFailed(jobId, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            markJobFailed(jobId, PROCESSING_ERROR_CODE, exception.getMessage());
        }
        return true;
    }

    @Transactional
    public int recoverStaleProcessingJobs(Duration staleThreshold) {
        return ingestionJobRepository.requeueStaleProcessingJobs(
                IngestionJobStatus.PROCESSING,
                IngestionJobStatus.PENDING,
                LocalDateTime.now().minus(staleThreshold),
                STALE_ERROR_CODE,
                STALE_ERROR_MESSAGE
        );
    }

    @Transactional
    public IngestionJobResponse updateJobStatus(Long jobId, UpdateIngestionJobStatusRequest request) {
        IngestionJob job = findJobEntity(jobId);
        job.setStatus(request.getStatus());

        if (request.getProgressPercent() != null) {
            job.setProgressPercent(request.getProgressPercent());
        }
        if (request.getAttemptCount() != null) {
            job.setAttemptCount(request.getAttemptCount());
        }
        if (request.getEmbeddingModel() != null) {
            job.setEmbeddingModel(request.getEmbeddingModel());
        }
        if (request.getLlmModel() != null) {
            job.setLlmModel(request.getLlmModel());
        }
        if (request.getChunkCount() != null) {
            job.setChunkCount(request.getChunkCount());
        }
        if (request.getEmbeddingCount() != null) {
            job.setEmbeddingCount(request.getEmbeddingCount());
        }

        job.setErrorCode(request.getErrorCode());
        job.setErrorMessage(request.getErrorMessage());
        job.setStartedAt(resolveStartedAt(job, request));
        job.setCompletedAt(resolveCompletedAt(job, request));

        return IngestionJobResponse.fromEntity(
                ingestionJobRepository.save(job),
                ingestionWarningService.listWarningsForJob(job.getId())
        );
    }

    private IngestionJob findJobEntity(Long jobId) {
        return ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingestion job not found with id: " + jobId));
    }

    private DocumentVersionProcessingRequest buildProcessingRequest(IngestionJob job, DocumentVersion version) {
        return DocumentVersionProcessingRequest.builder()
                .jobId(job.getId())
                .documentId(job.getDocumentId())
                .versionId(job.getVersionId())
                .storagePath(version.getFilePath())
                .originalFilename(version.getFileName())
                .contentType(version.getMimeType())
                .build();
    }

    private DocumentVersion findDocumentVersion(Long versionId) {
        return documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document version not found with id: " + versionId
                ));
    }

    @Transactional
    void markJobIndexed(Long jobId, DocumentVersionProcessingResponse response) {
        IngestionJob job = findJobEntity(jobId);
        job.setStatus(IngestionJobStatus.INDEXED);
        job.setProgressPercent(INDEXED_PROGRESS_PERCENT);
        job.setChunkCount(defaultCount(response.getChunkCount()));
        job.setEmbeddingCount(defaultCount(response.getEmbeddingCount()));
        job.setNextAttemptAt(null);
        job.setRetryable(false);
        job.setLastErrorCode(null);
        job.setLastErrorMessage(null);
        job.setCancellationRequested(false);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setCompletedAt(LocalDateTime.now());
        ingestionJobRepository.save(job);
    }

    @Transactional
    void markJobFailed(Long jobId, String errorCode, String errorMessage) {
        IngestionJob job = findJobEntity(jobId);
        job.setStatus(IngestionJobStatus.FAILED);
        String normalizedCode = normalize(errorCode) == null ? PROCESSING_ERROR_CODE : normalize(errorCode);
        String normalizedMessage = normalize(errorMessage);
        job.setRetryable(false);
        job.setNextAttemptAt(null);
        job.setLastErrorCode(normalizedCode);
        job.setLastErrorMessage(normalizedMessage);
        job.setCancellationRequested(false);
        job.setErrorCode(normalizedCode);
        job.setErrorMessage(normalizedMessage);
        job.setCompletedAt(LocalDateTime.now());
        ingestionJobRepository.save(job);
    }

    @Transactional
    void scheduleRetryOrFail(Long jobId, String errorCode, String errorMessage) {
        IngestionJob job = findJobEntity(jobId);
        String normalizedCode = normalize(errorCode) == null ? PROCESSING_ERROR_CODE : normalize(errorCode);
        String normalizedMessage = normalize(errorMessage);

        if (job.getAttemptCount() >= ingestionWorkerProperties.getMaxAttempts()) {
            job.setStatus(IngestionJobStatus.FAILED);
            job.setRetryable(false);
            job.setNextAttemptAt(null);
            job.setLastErrorCode(normalizedCode);
            job.setLastErrorMessage(normalizedMessage);
            job.setErrorCode(RETRY_LIMIT_EXCEEDED_ERROR_CODE);
            job.setErrorMessage("Retry limit exceeded. Last error: " + normalizedCode);
            job.setCompletedAt(LocalDateTime.now());
            ingestionJobRepository.save(job);
            return;
        }

        job.setStatus(IngestionJobStatus.PENDING);
        job.setProgressPercent(0);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setRetryable(true);
        job.setNextAttemptAt(LocalDateTime.now().plus(calculateRetryDelay(job.getAttemptCount())));
        job.setLastErrorCode(normalizedCode);
        job.setLastErrorMessage(normalizedMessage);
        job.setCancellationRequested(false);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        ingestionJobRepository.save(job);
    }

    private void cancelJob(IngestionJob job) {
        job.setStatus(IngestionJobStatus.CANCELLED);
        job.setProgressPercent(0);
        job.setStartedAt(null);
        job.setCompletedAt(LocalDateTime.now());
        job.setNextAttemptAt(null);
        job.setRetryable(false);
        job.setLastErrorCode(null);
        job.setLastErrorMessage(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
    }

    private void persistExtractionWarnings(IngestionJob job, ExtractedDocument extractedDocument) {
        List<String> warningMessages = documentTextExtractionService.buildWarnings(extractedDocument);
        if (warningMessages == null || warningMessages.isEmpty()) {
            return;
        }

        ingestionWarningService.addWarnings(
                job.getId(),
                job.getDocumentId(),
                job.getVersionId(),
                warningMessages
        );
    }

    private void persistResponseWarnings(IngestionJob job, DocumentVersionProcessingResponse response) {
        List<String> warningMessages = response.getWarningMessages();
        if (warningMessages == null || warningMessages.isEmpty()) {
            return;
        }

        ingestionWarningService.addWarnings(
                job.getId(),
                job.getDocumentId(),
                job.getVersionId(),
                warningMessages
        );
    }

    private DocumentVersionProcessingResponse withCounts(DocumentVersionProcessingResponse response,
                                                        int chunkCount,
                                                        int embeddingCount) {
        return DocumentVersionProcessingResponse.builder()
                .success(response.isSuccess())
                .chunkCount(chunkCount)
                .embeddingCount(embeddingCount)
                .warningMessages(response.getWarningMessages())
                .errorCode(response.getErrorCode())
                .errorMessage(response.getErrorMessage())
                .retryable(response.getRetryable())
                .build();
    }

    private LocalDateTime resolveStartedAt(IngestionJob job, UpdateIngestionJobStatusRequest request) {
        if (request.getStartedAt() != null) {
            return request.getStartedAt();
        }
        if (job.getStartedAt() != null) {
            return job.getStartedAt();
        }
        return request.getStatus() == IngestionJobStatus.PROCESSING ? LocalDateTime.now() : null;
    }

    private LocalDateTime resolveCompletedAt(IngestionJob job, UpdateIngestionJobStatusRequest request) {
        if (request.getCompletedAt() != null) {
            return request.getCompletedAt();
        }
        if (job.getCompletedAt() != null
                && request.getStatus() != IngestionJobStatus.PROCESSING
                && request.getStatus() != IngestionJobStatus.PENDING) {
            return job.getCompletedAt();
        }
        return switch (request.getStatus()) {
            case INDEXED, FAILED, CANCELLED -> LocalDateTime.now();
            case PENDING, PROCESSING -> null;
        };
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int defaultCount(Integer count) {
        return count == null ? 0 : count;
    }

    private Duration calculateRetryDelay(int attemptCount) {
        long initialMillis = ingestionWorkerProperties.getRetryInitialDelay().toMillis();
        double factor = Math.pow(ingestionWorkerProperties.getRetryBackoffMultiplier(),
                Math.max(0, attemptCount - 1));
        long computedMillis = Math.round(initialMillis * factor);
        long boundedMillis = Math.min(computedMillis, ingestionWorkerProperties.getRetryMaxDelay().toMillis());
        return Duration.ofMillis(Math.max(0L, boundedMillis));
    }
}
