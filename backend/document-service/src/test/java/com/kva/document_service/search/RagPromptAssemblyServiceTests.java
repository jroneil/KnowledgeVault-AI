package com.kva.document_service.search;

import com.kva.document_service.documents.Document;
import com.kva.document_service.documents.DocumentRepository;
import com.kva.document_service.metadata.DocumentMetadata;
import com.kva.document_service.metadata.DocumentMetadataRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagPromptAssemblyServiceTests {

    @Test
    void assembleBuildsPromptWithSourceMetadata() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        RagPromptAssemblyService service = new RagPromptAssemblyService(
                documentRepository,
                metadataRepository,
                properties(100)
        );

        when(documentRepository.findById(22L)).thenReturn(Optional.of(Document.builder()
                .id(22L)
                .title("Compressor Manual")
                .build()));
        when(metadataRepository.findByDocumentId(22L)).thenReturn(Optional.of(DocumentMetadata.builder()
                .documentId(22L)
                .product("Compressor X")
                .revision("B")
                .category("Maintenance")
                .build()));

        RagPromptPackage promptPackage = service.assemble(
                "What is the maintenance interval?",
                List.of(result(11L, 22L, 33L, 2, 0, 7, 7, 7, 12,
                        "Maintenance interval is every 500 hours.", 0.92d))
        );

        assertEquals(1, promptPackage.getTotalContexts());
        assertEquals(12, promptPackage.getTotalEstimatedTokens());
        assertTrue(promptPackage.getPrompt().contains("Question:"));
        assertTrue(promptPackage.getPrompt().contains("Compressor Manual"));
        assertTrue(promptPackage.getPrompt().contains("Revision: B"));
        assertTrue(promptPackage.getPrompt().contains("Page: 7"));
        assertTrue(promptPackage.getPrompt().contains("Maintenance interval is every 500 hours."));
        assertEquals("Compressor Manual", promptPackage.getContexts().get(0).getDocumentTitle());
        assertEquals("B", promptPackage.getContexts().get(0).getRevision());
        assertEquals("Compressor X", promptPackage.getContexts().get(0).getProduct());
    }

    @Test
    void assembleEnforcesContextBudget() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        RagPromptAssemblyService service = new RagPromptAssemblyService(
                documentRepository,
                metadataRepository,
                properties(10)
        );

        when(documentRepository.findById(22L)).thenReturn(Optional.of(Document.builder().id(22L).title("Doc A").build()));
        when(documentRepository.findById(23L)).thenReturn(Optional.of(Document.builder().id(23L).title("Doc B").build()));
        when(metadataRepository.findByDocumentId(22L)).thenReturn(Optional.empty());
        when(metadataRepository.findByDocumentId(23L)).thenReturn(Optional.empty());

        RagPromptPackage promptPackage = service.assemble(
                "Question",
                List.of(
                        result(11L, 22L, 33L, 1, 0, 1, 1, 1, 6, "first context", 0.95d),
                        result(12L, 23L, 34L, 1, 1, 2, 2, 2, 6, "second context", 0.90d)
                )
        );

        assertEquals(1, promptPackage.getTotalContexts());
        assertEquals(6, promptPackage.getTotalEstimatedTokens());
        assertTrue(promptPackage.isTruncated());
        assertTrue(promptPackage.getPrompt().contains("first context"));
        assertFalse(promptPackage.getPrompt().contains("second context"));
    }

    @Test
    void assembleDeduplicatesRepeatedChunksAndPreservesFirstRankOrder() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        RagPromptAssemblyService service = new RagPromptAssemblyService(
                documentRepository,
                metadataRepository,
                properties(100)
        );

        when(documentRepository.findById(22L)).thenReturn(Optional.of(Document.builder().id(22L).title("Doc A").build()));
        when(documentRepository.findById(23L)).thenReturn(Optional.of(Document.builder().id(23L).title("Doc B").build()));
        when(metadataRepository.findByDocumentId(22L)).thenReturn(Optional.empty());
        when(metadataRepository.findByDocumentId(23L)).thenReturn(Optional.empty());

        RagPromptPackage promptPackage = service.assemble(
                "Question",
                List.of(
                        result(11L, 22L, 33L, 1, 0, 1, 1, 1, 5, "first context", 0.99d),
                        result(11L, 22L, 33L, 1, 0, 1, 1, 1, 5, "first context", 0.98d),
                        result(12L, 23L, 34L, 1, 1, 2, 2, 2, 5, "second context", 0.97d)
                )
        );

        assertEquals(2, promptPackage.getTotalContexts());
        assertEquals(1, promptPackage.getContexts().get(0).getRank());
        assertEquals(2, promptPackage.getContexts().get(1).getRank());
        assertEquals(11L, promptPackage.getContexts().get(0).getChunkId());
        assertEquals(12L, promptPackage.getContexts().get(1).getChunkId());
    }

    @Test
    void assembleReturnsDeterministicEmptyPromptWhenNoResultsExist() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentMetadataRepository metadataRepository = mock(DocumentMetadataRepository.class);
        RagPromptAssemblyService service = new RagPromptAssemblyService(
                documentRepository,
                metadataRepository,
                properties(100)
        );

        RagPromptPackage promptPackage = service.assemble("Question", List.of());

        assertEquals(0, promptPackage.getTotalContexts());
        assertEquals(0, promptPackage.getTotalEstimatedTokens());
        assertFalse(promptPackage.isTruncated());
        assertTrue(promptPackage.getPrompt().contains("(no relevant context retrieved)"));
    }

    private RagPromptAssemblyProperties properties(int maxContextTokens) {
        RagPromptAssemblyProperties properties = new RagPromptAssemblyProperties();
        properties.setMaxContextTokens(maxContextTokens);
        return properties;
    }

    private SemanticRetrievalResult result(Long chunkId,
                                           Long documentId,
                                           Long versionId,
                                           Integer versionNumber,
                                           Integer chunkIndex,
                                           Integer pageNumber,
                                           Integer sourcePageFrom,
                                           Integer sourcePageTo,
                                           Integer tokenCount,
                                           String content,
                                           Double similarityScore) {
        return SemanticRetrievalResult.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .versionId(versionId)
                .versionNumber(versionNumber)
                .chunkIndex(chunkIndex)
                .pageNumber(pageNumber)
                .sourcePageFrom(sourcePageFrom)
                .sourcePageTo(sourcePageTo)
                .tokenCount(tokenCount)
                .content(content)
                .similarityScore(similarityScore)
                .build();
    }
}
