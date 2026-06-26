package com.kva.document_service.versions;

import com.kva.document_service.documents.Document;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.documents.DocumentVersion;
import com.kva.document_service.documents.DocumentVersionRepository;
import com.kva.document_service.ingestion.IngestionJobService;
import com.kva.document_service.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionServiceTests {

    @Test
    void clearsCurrentFlagBeforeSavingReplacementVersion() {
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        IngestionJobService ingestionJobService = mock(IngestionJobService.class);
        VersionService service = new VersionService(
                versionRepository,
                documentRepository,
                fileStorageService,
                ingestionJobService
        );

        Document document = Document.builder()
                .id(42L)
                .collectionId(7L)
                .status("ACTIVE")
                .currentVersion(1)
                .build();
        DocumentVersion current = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .versionNumber(1)
                .isCurrent(true)
                .build();
        DocumentVersion saved = DocumentVersion.builder()
                .id(101L)
                .documentId(42L)
                .versionNumber(2)
                .isCurrent(true)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "replacement.txt",
                "text/plain",
                "version two".getBytes()
        );

        when(documentRepository.findById(42L)).thenReturn(Optional.of(document));
        when(versionRepository.findCurrentVersion(42L)).thenReturn(Optional.of(current));
        when(fileStorageService.storeOriginalFile(file, 7L, 42L, 2))
                .thenReturn("/storage/documents/originals/7/42/v2.txt");
        when(fileStorageService.sanitizeFilename("replacement.txt")).thenReturn("replacement.txt");
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(saved);

        DocumentVersion result = service.uploadNewVersion(42L, file, 9L);

        InOrder databaseOrder = inOrder(versionRepository, documentRepository);
        databaseOrder.verify(versionRepository).markPreviousVersionsNotCurrent(42L);
        databaseOrder.verify(versionRepository).save(any(DocumentVersion.class));
        databaseOrder.verify(documentRepository).update(document);
        verify(ingestionJobService).createPendingJobIfAbsent(42L, 101L);
        assertEquals(2, result.getVersionNumber());
        assertEquals(2, document.getCurrentVersion());
    }

    @Test
    void deletesStoredFileWhenDatabasePersistenceFails() {
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        IngestionJobService ingestionJobService = mock(IngestionJobService.class);
        VersionService service = new VersionService(
                versionRepository,
                documentRepository,
                fileStorageService,
                ingestionJobService
        );
        Document document = Document.builder()
                .id(42L)
                .collectionId(7L)
                .status("ACTIVE")
                .currentVersion(1)
                .build();
        DocumentVersion current = DocumentVersion.builder()
                .documentId(42L)
                .versionNumber(1)
                .isCurrent(true)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "replacement.txt",
                "text/plain",
                "version two".getBytes()
        );
        String storedPath = "/storage/documents/originals/7/42/v2.txt";

        when(documentRepository.findById(42L)).thenReturn(Optional.of(document));
        when(versionRepository.findCurrentVersion(42L)).thenReturn(Optional.of(current));
        when(fileStorageService.storeOriginalFile(file, 7L, 42L, 2)).thenReturn(storedPath);
        when(fileStorageService.sanitizeFilename("replacement.txt")).thenReturn("replacement.txt");
        doThrow(new RuntimeException("database unavailable"))
                .when(versionRepository).save(any(DocumentVersion.class));

        assertThrows(RuntimeException.class, () -> service.uploadNewVersion(42L, file, 9L));
        verify(fileStorageService).deleteFile(storedPath);
        verify(ingestionJobService, never()).createPendingJobIfAbsent(any(), any());
    }

    @Test
    void rejectsDeletingOnlyVersionUnlessDocumentIsDeleted() {
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        IngestionJobService ingestionJobService = mock(IngestionJobService.class);
        VersionService service = new VersionService(
                versionRepository,
                documentRepository,
                fileStorageService,
                ingestionJobService
        );

        Document document = Document.builder()
                .id(42L)
                .status("ACTIVE")
                .build();
        DocumentVersion version = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .versionNumber(1)
                .isCurrent(false)
                .filePath("/storage/documents/originals/7/42/v1.txt")
                .build();

        when(documentRepository.findById(42L)).thenReturn(Optional.of(document));
        when(versionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(versionRepository.countByDocumentId(42L)).thenReturn(1L);

        assertThrows(com.kva.document_service.common.exceptions.BusinessException.class,
                () -> service.deleteVersion(42L, 100L, 9L));
    }
}
