package com.kva.document_service.documents;

import com.kva.document_service.collections.Collection;
import com.kva.document_service.collections.CollectionRepository;
import com.kva.document_service.documents.dto.DocumentUploadResponse;
import com.kva.document_service.documents.dto.UploadDocumentRequest;
import com.kva.document_service.ingestion.IngestionJobService;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import com.kva.document_service.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTests {

    @Test
    void uploadDocumentCreatesPendingIngestionJob() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        CollectionRepository collectionRepository = mock(CollectionRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        IngestionJobService ingestionJobService = mock(IngestionJobService.class);
        DocumentService service = new DocumentService(
                documentRepository,
                versionRepository,
                metadataRepository,
                collectionRepository,
                fileStorageService,
                ingestionJobService
        );

        UploadDocumentRequest request = UploadDocumentRequest.builder()
                .collectionId(7L)
                .title("Phase 2")
                .description("Initial upload")
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.txt",
                "text/plain",
                "document body".getBytes()
        );
        Collection collection = Collection.builder()
                .id(7L)
                .name("Operations")
                .isActive(true)
                .build();
        Document savedDocument = Document.builder()
                .id(42L)
                .collectionId(7L)
                .title("Phase 2")
                .description("Initial upload")
                .status("ACTIVE")
                .currentVersion(1)
                .createdBy(9L)
                .build();
        DocumentVersion savedVersion = DocumentVersion.builder()
                .id(100L)
                .documentId(42L)
                .versionNumber(1)
                .fileName("manual.txt")
                .filePath("/storage/originals/7/42/v1.txt")
                .fileSize((long) file.getBytes().length)
                .mimeType("text/plain")
                .uploadedBy(9L)
                .isCurrent(true)
                .build();

        when(collectionRepository.findById(7L)).thenReturn(Optional.of(collection));
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        when(fileStorageService.storeOriginalFile(file, 7L, 42L, 1)).thenReturn(savedVersion.getFilePath());
        when(fileStorageService.sanitizeFilename("manual.txt")).thenReturn("manual.txt");
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(savedVersion);

        DocumentUploadResponse response = service.uploadDocument(request, file, 9L);

        verify(ingestionJobService).createPendingJobIfAbsent(42L, 100L);
        assertEquals(42L, response.getDocumentId());
        assertEquals(100L, response.getVersionId());
    }

    @Test
    void failedUploadDoesNotCreateIngestionJob() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        CollectionRepository collectionRepository = mock(CollectionRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        IngestionJobService ingestionJobService = mock(IngestionJobService.class);
        DocumentService service = new DocumentService(
                documentRepository,
                versionRepository,
                metadataRepository,
                collectionRepository,
                fileStorageService,
                ingestionJobService
        );

        UploadDocumentRequest request = UploadDocumentRequest.builder()
                .collectionId(7L)
                .title("Phase 2")
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.txt",
                "text/plain",
                "document body".getBytes()
        );
        Collection collection = Collection.builder()
                .id(7L)
                .name("Operations")
                .isActive(true)
                .build();
        Document savedDocument = Document.builder()
                .id(42L)
                .collectionId(7L)
                .title("Phase 2")
                .status("ACTIVE")
                .currentVersion(1)
                .createdBy(9L)
                .build();
        String storedPath = "/storage/originals/7/42/v1.txt";

        when(collectionRepository.findById(7L)).thenReturn(Optional.of(collection));
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        when(fileStorageService.storeOriginalFile(file, 7L, 42L, 1)).thenReturn(storedPath);
        when(fileStorageService.sanitizeFilename("manual.txt")).thenReturn("manual.txt");
        doThrow(new RuntimeException("database unavailable"))
                .when(versionRepository).save(any(DocumentVersion.class));

        assertThrows(RuntimeException.class, () -> service.uploadDocument(request, file, 9L));

        verify(fileStorageService).deleteFile(storedPath);
        verify(ingestionJobService, never()).createPendingJobIfAbsent(any(), any());
    }
}
