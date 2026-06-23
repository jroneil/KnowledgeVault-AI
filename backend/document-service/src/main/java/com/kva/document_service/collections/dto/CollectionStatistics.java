package com.kva.document_service.collections.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionStatistics {
    private Long collectionId;
    private String collectionName;
    private Long documentCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
}