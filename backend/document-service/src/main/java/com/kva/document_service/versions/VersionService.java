package com.kva.document_service.versions;

import com.kva.document_service.common.exceptions.BusinessException;
import com.kva.document_service.common.exceptions.ResourceNotFoundException;
import com.kva.document_service.documents.Document;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.documents.DocumentVersion;
import com.kva.document_service.documents.DocumentVersionRepository;
import com.kva.document_service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VersionService {

    private final DocumentVersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public DocumentVersion uploadNewVersion(Long documentId, MultipartFile file, Long userId) {
        // Verify document exists
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (!"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Cannot upload new version for inactive document");
        }

        // Get current version number
        Optional<DocumentVersion> currentVersion = versionRepository.findCurrentVersion(documentId);
        int newVersionNumber = currentVersion
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);

        try {
            // Mark previous versions as not current
            versionRepository.markPreviousVersionsNotCurrent(documentId);

            // Store new file
            String filePath = fileStorageService.storeOriginalFile(
                    file,
                    document.getCollectionId(),
                    documentId,
                    newVersionNumber
            );

            // Create new version record
            DocumentVersion newVersion = DocumentVersion.builder()
                    .documentId(documentId)
                    .versionNumber(newVersionNumber)
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .uploadedBy(userId)
                    .uploadDate(LocalDateTime.now())
                    .isCurrent(true)
                    .build();

            DocumentVersion saved = versionRepository.save(newVersion);

            // Update document current version
            document.setCurrentVersion(newVersionNumber);
            documentRepository.update(document);

            log.info("Created new version: {} for document: {} by user: {}", newVersionNumber, documentId, userId);
            return saved;

        } catch (Exception e) {
            log.error("Failed to upload new version for document: {}", documentId, e);
            throw new BusinessException("Failed to upload new version: " + e.getMessage(), e);
        }
    }

    public List<DocumentVersion> getVersionHistory(Long documentId) {
        // Verify document exists
        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException("Document not found with id: " + documentId);
        }

        List<DocumentVersion> versions = versionRepository.findByDocumentId(documentId);
        log.info("Retrieved {} versions for document: {}", versions.size(), documentId);
        return versions;
    }

    public Optional<DocumentVersion> getCurrentVersion(Long documentId) {
        return versionRepository.findCurrentVersion(documentId);
    }

    public Optional<DocumentVersion> getVersion(Long versionId) {
        return versionRepository.findById(versionId);
    }

    public Optional<DocumentVersion> getVersionByNumber(Long documentId, Integer versionNumber) {
        return versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
    }

    @Transactional
    public void setCurrentVersion(Long documentId, Integer versionNumber, Long userId) {
        // Verify document exists
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        // Find the version to set as current
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionNumber));

        // Mark all versions as not current
        versionRepository.markPreviousVersionsNotCurrent(documentId);

        // Set the new current version
        version.setIsCurrent(true);
        versionRepository.update(version);

        // Update document current version
        document.setCurrentVersion(versionNumber);
        documentRepository.update(document);

        log.info("Set version: {} as current for document: {} by user: {}", versionNumber, documentId, userId);
    }

    @Transactional
    public void deleteVersion(Long versionId, Long userId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId));

        // Cannot delete current version
        if (version.getIsCurrent()) {
            throw new BusinessException("Cannot delete current version. Please set another version as current first.");
        }

        // Delete file from storage
        fileStorageService.deleteFile(version.getFilePath());

        // Delete version record
        versionRepository.deleteById(versionId);

        log.info("Deleted version: {} by user: {}", versionId, userId);
    }

    public long getTotalVersions() {
        return versionRepository.count();
    }

    public long getVersionsByDocument(Long documentId) {
        return versionRepository.countByDocumentId(documentId);
    }
}