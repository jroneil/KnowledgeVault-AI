package com.kva.document_service.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadDocumentRequest {
    @NotNull(message = "Collection ID is required")
    private Long collectionId;

    @NotBlank(message = "Document title is required")
    @Size(min = 1, max = 500, message = "Title must be between 1 and 500 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    // Metadata fields
    private String product;
    private String revision;
    private String department;
    private String manufacturer;
    private String category;
    private String effectiveDate;
    private String tags; // Comma-separated tags
}