package com.kva.document_service.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByVersionIdOrderByChunkIndexAsc(Long versionId);

    void deleteByVersionId(Long versionId);
}
