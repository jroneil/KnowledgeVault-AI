package com.kva.document_service.documents;

import com.kva.document_service.collections.Collection;
import com.kva.document_service.collections.CollectionRepository;
import com.kva.document_service.common.exceptions.BusinessException;
import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.documents.dto.DocumentUploadResponse;
import com.kva.document_service.documents.dto.UpdateDocumentRequest;
import com.kva.document_service.documents.dto.UploadDocumentRequest;
import com.kva.document_service.ingestion.IngestionJobService;
import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import com.kva.document_service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentMetadataRepository metadataRepository;
    private final CollectionRepository collectionRepository;
    private final FileStorageService fileStorageService;
    private final IngestionJobService ingestionJobService;

    @Transactional
    public DocumentUploadResponse uploadDocument(UploadDocumentRequest request,
                                                MultipartFile file,
                                                Long userId) {
        DocumentMetadata metadata = hasMetadata(request)
                ? createMetadataFromRequest(request, null)
                : null;
        return uploadDocumentInternal(request.getCollectionId(), request.getTitle(), request.getDescription(), metadata, file, userId);
    }

    @Transactional
    public DocumentUploadResponse uploadDocumentWithResolvedMetadata(Long collectionId,
                                                                     String title,
                                                                     String description,
                                                                     DocumentMetadata metadata,
                                                                     MultipartFile file,
                                                                     Long userId) {
        return uploadDocumentInternal(collectionId, title, description, metadata, file, userId);
    }

    private DocumentUploadResponse uploadDocumentInternal(Long collectionId,
                                                          String title,
                                                          String description,
                                                          DocumentMetadata metadata,
                                                          MultipartFile file,
                                                          Long userId) {
        // Validate collection exists
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

        if (!collection.getIsActive()) {
            throw new BusinessException("Cannot upload to inactive collection: " + collection.getName());
        }

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is required for document upload");
        }

        String storedFilePath = null;
        try {
            // Create document record
            Document document = Document.builder()
                    .collectionId(collection.getId())
                    .title(title)
                    .description(description)
                    .status("ACTIVE")
                    .currentVersion(1)
                    .createdBy(userId)
                    .build();

            Document savedDocument = documentRepository.save(document);
            log.info("Created document: {} in collection: {}", savedDocument.getId(), collection.getId());

            // Store file and create version record
            storedFilePath = fileStorageService.storeOriginalFile(
                    file,
                    collection.getId(),
                    savedDocument.getId(),
                    1
            );

            DocumentVersion version = DocumentVersion.builder()
                    .documentId(savedDocument.getId())
                    .versionNumber(1)
                    .fileName(fileStorageService.sanitizeFilename(file.getOriginalFilename()))
                    .filePath(storedFilePath)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .uploadedBy(userId)
                    .uploadDate(LocalDateTime.now())
                    .isCurrent(true)
                    .build();

            DocumentVersion savedVersion = versionRepository.save(version);
            log.info("Created version: {} for document: {}", savedVersion.getId(), savedDocument.getId());

            // Create metadata if provided
            if (metadata != null) {
                metadata.setDocumentId(savedDocument.getId());
                metadataRepository.save(metadata);
                log.info("Created metadata for document: {}", savedDocument.getId());
            }

            ingestionJobService.createPendingJobIfAbsent(savedDocument.getId(), savedVersion.getId());

            return DocumentUploadResponse.builder()
                    .documentId(savedDocument.getId())
                    .collectionId(collection.getId())
                    .title(savedDocument.getTitle())
                    .currentVersion(savedDocument.getCurrentVersion())
                    .versionId(savedVersion.getId())
                    .fileName(savedVersion.getFileName())
                    .fileSize(savedVersion.getFileSize())
                    .uploadedAt(savedVersion.getUploadDate())
                    .message("Document uploaded successfully")
                    .build();

        } catch (Exception e) {
            if (storedFilePath != null) {
                try {
                    fileStorageService.deleteFile(storedFilePath);
                } catch (Exception cleanupException) {
                    log.warn("Failed to clean up stored file after upload rollback: {}",
                            storedFilePath, cleanupException);
                }
            }
            log.error("Failed to upload document: {}", title, e);
            throw new BusinessException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Document getDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        return document;
    }

    @Transactional
    public Document updateDocument(Long id, UpdateDocumentRequest request, Long userId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (request.getTitle() != null) {
            document.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            document.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            validateStatus(request.getStatus());
            document.setStatus(request.getStatus());
        }

        Document updated = documentRepository.update(document);
        log.info("Updated document: {} by user: {}", id, userId);
        return updated;
    }

    @Transactional
    public void deleteDocument(Long id, Long userId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        // Soft delete by changing status
        document.setStatus("DELETED");
        documentRepository.update(document);

        log.info("Soft-deleted document: {} by user: {}", id, userId);
    }

    public List<Document> listDocuments() {
        return documentRepository.findAll();
    }

    public List<Document> listDocumentsByCollection(Long collectionId) {
        // Verify collection exists
        if (!collectionRepository.existsById(collectionId)) {
            throw new ResourceNotFoundException("Collection not found with id: " + collectionId);
        }
        return documentRepository.findByCollectionId(collectionId);
    }

    public List<Document> listDocumentsByStatus(String status) {
        validateStatus(status);
        return documentRepository.findByStatus(status);
    }

    public List<Document> searchDocuments(String searchTerm) {
        return documentRepository.searchByTitle(searchTerm);
    }

    public byte[] downloadDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (!"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Cannot download deleted or archived document");
        }

        // Get current version
        Optional<DocumentVersion> currentVersion = versionRepository.findCurrentVersion(documentId);
        if (currentVersion.isEmpty()) {
            throw new BusinessException("No current version found for document: " + documentId);
        }

        try {
            return fileStorageService.loadFile(currentVersion.get().getFilePath())
                    .getInputStream()
                    .readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download document: {}", documentId, e);
            throw new BusinessException("Failed to download document: " + e.getMessage(), e);
        }
    }

    public long getTotalDocuments() {
        return documentRepository.count();
    }

    public long getDocumentsByCollection(Long collectionId) {
        return documentRepository.countByCollectionId(collectionId);
    }

    private boolean hasMetadata(UploadDocumentRequest request) {
        return request.getProduct() != null || request.getRevision() != null ||
               request.getDepartment() != null || request.getManufacturer() != null ||
               request.getModel() != null || request.getDocumentType() != null ||
               request.getDocumentNumber() != null || request.getLanguage() != null ||
               request.getCategory() != null || request.getEffectiveDate() != null ||
               request.getPublicationDate() != null || request.getPageCount() != null ||
               (request.getTags() != null && !request.getTags().isEmpty());
    }

    private DocumentMetadata createMetadataFromRequest(UploadDocumentRequest request, Long documentId) {
        List<String> tagsList = null;
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            tagsList = Arrays.asList(request.getTags().split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        LocalDate effectiveDate = null;
        if (request.getEffectiveDate() != null && !request.getEffectiveDate().isEmpty()) {
            try {
                effectiveDate = LocalDate.parse(request.getEffectiveDate());
            } catch (Exception e) {
                log.warn("Invalid effective date format: {}", request.getEffectiveDate());
            }
        }

        LocalDate publicationDate = null;
        if (request.getPublicationDate() != null && !request.getPublicationDate().isEmpty()) {
            try {
                publicationDate = LocalDate.parse(request.getPublicationDate());
            } catch (Exception e) {
                log.warn("Invalid publication date format: {}", request.getPublicationDate());
            }
        }

        return DocumentMetadata.builder()
                .documentId(documentId)
                .product(request.getProduct())
                .revision(request.getRevision())
                .department(request.getDepartment())
                .manufacturer(request.getManufacturer())
                .model(request.getModel())
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .language(request.getLanguage())
                .category(request.getCategory())
                .effectiveDate(effectiveDate)
                .publicationDate(publicationDate)
                .pageCount(request.getPageCount())
                .tags(tagsList)
                .build();
    }

    private void validateStatus(String status) {
        if (!status.equals("ACTIVE") && !status.equals("ARCHIVED") && !status.equals("DELETED")) {
            throw new BusinessException("Invalid status: " + status + ". Must be ACTIVE, ARCHIVED, or DELETED");
        }
    }
}
