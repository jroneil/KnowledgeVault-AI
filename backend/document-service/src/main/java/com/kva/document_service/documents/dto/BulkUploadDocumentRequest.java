package com.kva.document_service.documents.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadDocumentRequest {
    @NotNull(message = "Collection ID is required")
    private Long collectionId;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private String title;
    private String product;
    private String revision;
    private String department;
    private String manufacturer;
    private String model;
    private String documentType;
    private String documentNumber;
    private String language;
    private String category;
    private String effectiveDate;
    private String publicationDate;
    private Integer pageCount;
    private String tags;
}
