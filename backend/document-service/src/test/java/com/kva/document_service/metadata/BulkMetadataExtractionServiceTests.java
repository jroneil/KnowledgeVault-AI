package com.kva.document_service.metadata;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkMetadataExtractionServiceTests {

    private final BulkMetadataExtractionService service = new BulkMetadataExtractionService();

    @Test
    void extractsMetadataFromPdfPropertiesFilenameAndFirstPageText() throws Exception {
        byte[] pdfBytes = createPdf(
                "Hydraulic Pump Manual",
                """
                Manufacturer: Acme Corp
                Model: HX-200
                Document Number: DOC-7788
                Revision: B
                Language: English
                Published: 2025-03-14
                """
        );
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "acme-hx-200-manual-revB.pdf",
                "application/pdf",
                pdfBytes
        );

        DocumentMetadataExtractionResult result = service.extract(file);

        assertEquals("Hydraulic Pump Manual", result.getExtractedTitle());
        assertEquals("Acme Corp", result.getExtractedManufacturer());
        assertEquals("HX-200", result.getExtractedModel());
        assertEquals("DOC-7788", result.getExtractedDocumentNumber());
        assertEquals("B", result.getExtractedRevision());
        assertEquals("English", result.getExtractedLanguage());
        assertEquals(1, result.getExtractedPageCount());
        assertEquals("pdf-properties,filename,first-page-text", result.getSourceSummary());
        assertNotNull(result.getConfidenceByField());
        assertTrue(result.getConfidenceByField().containsKey("title"));
    }

    @Test
    void extractsTitleAndFlagsReviewFromTextWhenConfidenceIsLow() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "widget-qc-guide-en.txt",
                "text/plain",
                """
                Widget QC Guide
                Quick checks for incoming inspection.
                """.getBytes()
        );

        DocumentMetadataExtractionResult result = service.extract(file);

        assertEquals("Widget QC Guide", result.getExtractedTitle());
        assertEquals("Guide", result.getExtractedDocumentType());
        assertEquals("English", result.getExtractedLanguage());
        assertFalse(result.getConfidenceByField().isEmpty());
        assertTrue(result.isNeedsReview());
    }

    private byte[] createPdf(String title, String firstPageText) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(title);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                for (String line : firstPageText.split("\\R")) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -16);
                }
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
