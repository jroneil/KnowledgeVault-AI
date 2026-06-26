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
@Table(name = "document_extraction_pages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExtractionPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_id", nullable = false)
    private DocumentExtraction extraction;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "ocr_applied", nullable = false)
    private Boolean ocrApplied;

    @Column(name = "character_count", nullable = false)
    private Integer characterCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
