package com.kva.document_service.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IngestionWarningRepository extends JpaRepository<IngestionWarning, Long> {

    List<IngestionWarning> findByIngestionJobIdOrderByCreatedAtAscIdAsc(Long ingestionJobId);

    List<IngestionWarning> findByIngestionJobIdInOrderByIngestionJobIdAscCreatedAtAscIdAsc(
            Collection<Long> ingestionJobIds);

    List<IngestionWarning> findByVersionIdOrderByCreatedAtAscIdAsc(Long versionId);

    void deleteByIngestionJobId(Long ingestionJobId);
}
