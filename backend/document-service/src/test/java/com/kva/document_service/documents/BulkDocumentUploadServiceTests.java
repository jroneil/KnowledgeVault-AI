package com.kva.document_service.documents;

import com.kva.document_service.documents.dto.BulkUploadDocumentRequest;
import com.kva.document_service.documents.dto.BulkUploadResponse;
import com.kva.document_service.documents.dto.DocumentUploadResponse;
import com.kva.document_service.metadata.BulkMetadataExtractionService;
import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataExtractionResult;
import com.kva.document_service.metadata.DocumentMetadataExtractionResultRepository;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkDocumentUploadServiceTests {

    @Test
    void bulkUploadAllowsPartialSuccessAndPersistsExtractionResults() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        DocumentMetadataExtractionResultRepository extractionRepository = mock(DocumentMetadataExtractionResultRepository.class);
        BulkMetadataExtractionService extractionService = mock(BulkMetadataExtractionService.class);

        BulkDocumentUploadService service = new BulkDocumentUploadService(
                documentService,
                metadataRepository,
                extractionRepository,
                extractionService
        );

        BulkUploadDocumentRequest request = BulkUploadDocumentRequest.builder()
                .collectionId(7L)
                .manufacturer("Manual Manufacturer")
                .tags("priority,review")
                .build();

        MockMultipartFile goodFile = new MockMultipartFile("files", "good.pdf", "application/pdf", "pdf".getBytes());
        MockMultipartFile badFile = new MockMultipartFile("files", "bad.pdf", "application/pdf", "pdf".getBytes());

        DocumentMetadataExtractionResult extracted = DocumentMetadataExtractionResult.builder()
                .extractedTitle("Extracted Title")
                .extractedManufacturer("Extracted Manufacturer")
                .extractedModel("HX-200")
                .extractedDocumentType("Manual")
                .extractedDocumentNumber("DOC-100")
                .extractedRevision("A")
                .extractedLanguage("English")
                .extractedPublicationDate(LocalDate.of(2025, 3, 14))
                .extractedPageCount(12)
                .extractedTags(List.of("manual"))
                .confidenceByField(Map.of("title", 0.9))
                .needsReview(false)
                .sourceSummary("pdf-properties,filename,first-page-text")
                .build();

        when(extractionService.extract(goodFile)).thenReturn(extracted);
        when(extractionService.extract(badFile)).thenThrow(new RuntimeException("unsupported file"));
        when(documentService.uploadDocumentWithResolvedMetadata(eq(7L), eq("Extracted Title"), eq(null), any(DocumentMetadata.class), eq(goodFile), eq(5L)))
                .thenReturn(DocumentUploadResponse.builder()
                        .documentId(101L)
                        .versionId(201L)
                        .title("Extracted Title")
                        .message("Document uploaded successfully")
                        .build());
        when(metadataRepository.findByDocumentId(101L)).thenReturn(Optional.of(
                DocumentMetadata.builder()
                        .documentId(101L)
                        .manufacturer("Manual Manufacturer")
                        .model("HX-200")
                        .documentType("Manual")
                        .documentNumber("DOC-100")
                        .language("English")
                        .publicationDate(LocalDate.of(2025, 3, 14))
                        .pageCount(12)
                        .tags(List.of("priority", "review"))
                        .build()
        ));
        when(extractionRepository.upsert(any(DocumentMetadataExtractionResult.class))).thenAnswer(invocation -> {
            DocumentMetadataExtractionResult value = invocation.getArgument(0);
            value.setId(301L);
            value.setDocumentId(101L);
            return value;
        });

        BulkUploadResponse response = service.uploadDocuments(request, new MockMultipartFile[]{goodFile, badFile}, 5L);

        ArgumentCaptor<DocumentMetadata> metadataCaptor = ArgumentCaptor.forClass(DocumentMetadata.class);

        assertEquals(2, response.getProcessedCount());
        assertEquals(1, response.getSucceededCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(0, response.getNeedsReviewCount());
        assertTrue(response.getResults().get(0).isSuccess());
        assertEquals("Manual Manufacturer", response.getResults().get(0).getMetadata().getManufacturer());
        assertEquals(List.of("priority", "review"), response.getResults().get(0).getMetadata().getTags());
        assertFalse(response.getResults().get(0).isNeedsReview());
        assertFalse(response.getResults().get(1).isSuccess());

        verify(documentService).uploadDocumentWithResolvedMetadata(eq(7L), eq("Extracted Title"), eq(null), metadataCaptor.capture(), eq(goodFile), eq(5L));
        assertEquals("Manual Manufacturer", metadataCaptor.getValue().getManufacturer());
        assertEquals("HX-200", metadataCaptor.getValue().getModel());
        assertEquals("Manual", metadataCaptor.getValue().getDocumentType());
        assertEquals(List.of("priority", "review"), metadataCaptor.getValue().getTags());
        verify(extractionRepository).upsert(any(DocumentMetadataExtractionResult.class));
        verify(documentService, never()).uploadDocumentWithResolvedMetadata(eq(7L), anyString(), eq(null), any(DocumentMetadata.class), eq(badFile), eq(5L));
    }
}
