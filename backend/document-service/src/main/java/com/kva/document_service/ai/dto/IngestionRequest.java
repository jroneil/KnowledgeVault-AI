package com.kva.document_service.ai.dto;

/**
 * Request to start document ingestion process
 */
public class IngestionRequest {
    private Long documentId;
    private Long versionId;
    private String filePath;
    private String mimeType;
    
    public IngestionRequest() {}
    
    public IngestionRequest(Long documentId, Long versionId, String filePath, String mimeType) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.filePath = filePath;
        this.mimeType = mimeType;
    }
    
    // Getters and Setters
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
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}