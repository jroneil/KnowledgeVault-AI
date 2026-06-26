package com.kva.document_service.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkingServiceTests {

    @Test
    void createsChunksWithOrderingAndOverlap() {
        DocumentChunkingService service = newService(30, 10);
        DocumentExtraction extraction = extraction(
                page(1, "Page one has enough text to require multiple chunk windows."),
                page(2, "Page two continues the extracted content for chunk testing.")
        );

        List<DocumentChunkDraft> chunks = service.createChunks(extraction);

        assertTrue(chunks.size() >= 3);
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(1, chunks.get(1).getChunkIndex());
        String suffix = chunks.get(0).getContent().substring(chunks.get(0).getContent().length() - 10);
        assertTrue(chunks.get(1).getContent().startsWith(suffix));
    }

    @Test
    void preservesSourcePageReferencesForChunks() {
        DocumentChunkingService service = newService(40, 5);
        DocumentExtraction extraction = extraction(
                page(3, "First page content that fills a chunk."),
                page(4, "Second page content that also fills a chunk.")
        );

        List<DocumentChunkDraft> chunks = service.createChunks(extraction);

        assertEquals(3, chunks.get(0).getSourcePageFrom());
        assertTrue(chunks.get(0).getSourcePageTo() >= 3);
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getSourcePageTo() == 4));
    }

    @Test
    void chunkCanSpanMultiplePages() {
        DocumentChunkingService service = newService(90, 10);
        DocumentExtraction extraction = extraction(
                page(1, "This first page has moderate content."),
                page(2, "This second page continues enough text so one chunk spans both pages.")
        );

        List<DocumentChunkDraft> chunks = service.createChunks(extraction);

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getSourcePageFrom() == 1 && chunk.getSourcePageTo() == 2));
    }

    private DocumentChunkingService newService(int chunkSize, int overlap) {
        IngestionChunkingProperties properties = new IngestionChunkingProperties();
        properties.setEnabled(true);
        properties.setChunkSize(chunkSize);
        properties.setOverlap(overlap);
        return new DocumentChunkingService(properties);
    }

    private DocumentExtraction extraction(DocumentExtractionPage... pages) {
        DocumentExtraction extraction = DocumentExtraction.builder()
                .ingestionJobId(55L)
                .documentId(42L)
                .versionId(100L)
                .extractorType("PDF")
                .pageCount(pages.length)
                .characterCount(0)
                .build();

        int characters = 0;
        for (DocumentExtractionPage page : pages) {
            extraction.addPage(page);
            characters += page.getCharacterCount();
        }
        extraction.setCharacterCount(characters);
        return extraction;
    }

    private DocumentExtractionPage page(int pageNumber, String text) {
        return DocumentExtractionPage.builder()
                .pageNumber(pageNumber)
                .extractedText(text)
                .originalText(text)
                .ocrText(null)
                .ocrApplied(false)
                .characterCount(text.length())
                .build();
    }
}
