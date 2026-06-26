package com.kva.document_service.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    List<IngestionJob> findByDocumentIdOrderByCreatedAtDescIdDesc(Long documentId);

    Optional<IngestionJob> findByDocumentIdAndVersionId(Long documentId, Long versionId);

    @Query("""
            select job.id
            from IngestionJob job
            where job.status = :status
              and (job.nextAttemptAt is null or job.nextAttemptAt <= :readyAt)
            order by job.createdAt asc, job.id asc
            """)
    List<Long> findReadyPendingJobIds(
            @Param("status") IngestionJobStatus status,
            @Param("readyAt") LocalDateTime readyAt,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IngestionJob job
            set job.status = :processingStatus,
                job.progressPercent = :progressPercent,
                job.attemptCount = job.attemptCount + 1,
                job.startedAt = :startedAt,
                job.completedAt = null,
                job.nextAttemptAt = null,
                job.retryable = false,
                job.errorCode = null,
                job.errorMessage = null
            where job.id = :jobId and job.status = :pendingStatus and job.cancellationRequested = false
            """)
    int claimPendingJob(
            @Param("jobId") Long jobId,
            @Param("pendingStatus") IngestionJobStatus pendingStatus,
            @Param("processingStatus") IngestionJobStatus processingStatus,
            @Param("progressPercent") Integer progressPercent,
            @Param("startedAt") LocalDateTime startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IngestionJob job
            set job.status = :indexedStatus,
                job.progressPercent = :progressPercent,
                job.completedAt = :completedAt,
                job.errorCode = null,
                job.errorMessage = null
            where job.id = :jobId and job.status = :processingStatus
            """)
    int markJobIndexed(
            @Param("jobId") Long jobId,
            @Param("processingStatus") IngestionJobStatus processingStatus,
            @Param("indexedStatus") IngestionJobStatus indexedStatus,
            @Param("progressPercent") Integer progressPercent,
            @Param("completedAt") LocalDateTime completedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IngestionJob job
            set job.status = :pendingStatus,
                job.progressPercent = 0,
                job.startedAt = null,
                job.completedAt = null,
                job.nextAttemptAt = null,
                job.retryable = true,
                job.errorCode = :errorCode,
                job.errorMessage = :errorMessage,
                job.lastErrorCode = :errorCode,
                job.lastErrorMessage = :errorMessage
            where job.status = :processingStatus
              and job.startedAt is not null
              and job.startedAt < :staleBefore
            """)
    int requeueStaleProcessingJobs(
            @Param("processingStatus") IngestionJobStatus processingStatus,
            @Param("pendingStatus") IngestionJobStatus pendingStatus,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);
}
