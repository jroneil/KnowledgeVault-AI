package com.kva.document_service.ingestion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_warnings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingestion_job_id", nullable = false)
    private Long ingestionJobId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "warning_code", length = 100)
    private String warningCode;

    @Column(name = "warning_message", nullable = false, columnDefinition = "TEXT")
    private String warningMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IngestionWarningSeverity severity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
