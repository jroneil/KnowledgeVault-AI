package com.kva.document_service.ai.dto;

import java.time.LocalDateTime;

/**
 * Status of an ingestion job
 */
public class IngestionJobStatus {
    private String jobId;
    private Long documentId;
    private Long versionId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private int progress; // 0-100
    private String message;
    private int chunksProcessed;
    private int embeddingsGenerated;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public IngestionJobStatus() {}
    
    // Getters and Setters
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    public Long getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }
    
    public Long getVersionId() {
        return versionId;
    }
    
    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public int getProgress() {
        return progress;
    }
    
    public void setProgress(int progress) {
        this.progress = progress;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public int getChunksProcessed() {
        return chunksProcessed;
    }
    
    public void setChunksProcessed(int chunksProcessed) {
        this.chunksProcessed = chunksProcessed;
    }
    
    public int getEmbeddingsGenerated() {
        return embeddingsGenerated;
    }
    
    public void setEmbeddingsGenerated(int embeddingsGenerated) {
        this.embeddingsGenerated = embeddingsGenerated;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    public boolean isProcessing() {
        return "PROCESSING".equals(status);
    }
}