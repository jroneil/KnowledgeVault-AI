package com.kva.document_service.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    private Long id;
    private Long documentId;
    private String product;
    private String revision;
    private String department;
    private String manufacturer;
    private String model;
    private String documentType;
    private String documentNumber;
    private String language;
    private List<String> tags;
    private String category;
    private LocalDate effectiveDate;
    private LocalDate publicationDate;
    private Integer pageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
