package com.kva.document_service.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, Long> {

    Optional<DocumentExtraction> findByIngestionJobId(Long ingestionJobId);

    Optional<DocumentExtraction> findByVersionId(Long versionId);

    void deleteByVersionId(Long versionId);
}
