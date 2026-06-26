package com.kva.document_service.ingestion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "document_extractions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingestion_job_id", nullable = false)
    private Long ingestionJobId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "extractor_type", nullable = false, length = 32)
    private String extractorType;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Column(name = "character_count", nullable = false)
    private Integer characterCount;

    @OneToMany(mappedBy = "extraction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageNumber ASC")
    @Builder.Default
    private List<DocumentExtractionPage> pages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addPage(DocumentExtractionPage page) {
        pages.add(page);
        page.setExtraction(this);
    }
}
