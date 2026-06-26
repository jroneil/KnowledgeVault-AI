package com.kva.document_service.ingestion.dto;

import com.kva.document_service.ingestion.IngestionWarning;
import com.kva.document_service.ingestion.IngestionWarningSeverity;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class IngestionWarningResponse {
    Long id;
    Long ingestionJobId;
    Long documentId;
    Long versionId;
    String warningCode;
    String warningMessage;
    IngestionWarningSeverity severity;
    LocalDateTime createdAt;

    public static IngestionWarningResponse fromEntity(IngestionWarning warning) {
        return IngestionWarningResponse.builder()
                .id(warning.getId())
                .ingestionJobId(warning.getIngestionJobId())
                .documentId(warning.getDocumentId())
                .versionId(warning.getVersionId())
                .warningCode(warning.getWarningCode())
                .warningMessage(warning.getWarningMessage())
                .severity(warning.getSeverity())
                .createdAt(warning.getCreatedAt())
                .build();
    }
}
