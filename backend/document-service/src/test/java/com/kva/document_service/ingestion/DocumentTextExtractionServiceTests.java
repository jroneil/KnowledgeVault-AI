package com.kva.document_service.ingestion;

import com.kva.document_service.documents.DocumentVersion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTextExtractionServiceTests {

    @Test
    void extractsPdfTextWithPageNumbers() throws IOException {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        DocumentTextExtractionService service = newService(ocrClient);
        Path pdfPath = createPdf(
                "First page text with enough characters for standard extraction.",
                "Second page text with enough characters for standard extraction."
        );

        try {
            ExtractedDocument extractedDocument = service.extract(DocumentVersion.builder()
                    .filePath(pdfPath.toString())
                    .fileName("sample.pdf")
                    .mimeType("application/pdf")
                    .build());

            assertEquals("PDF", extractedDocument.getExtractorType());
            assertEquals(2, extractedDocument.getPageCount());
            assertEquals(2, extractedDocument.getPages().size());
            assertEquals(1, extractedDocument.getPages().get(0).getPageNumber());
            assertTrue(extractedDocument.getPages().get(0).getExtractedText().contains("First page text"));
            assertEquals(2, extractedDocument.getPages().get(1).getPageNumber());
            assertTrue(extractedDocument.getPages().get(1).getExtractedText().contains("Second page text"));
            verify(ocrClient, never()).performOcr(any());
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void detectsLowTextPageAndRunsOcrOnlyForThatPage() throws IOException {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        when(ocrClient.performOcr(any())).thenReturn(DocumentPageOcrResult.builder()
                .success(true)
                .text("OCR text for blank page")
                .confidence(0.95d)
                .build());
        DocumentTextExtractionService service = newService(ocrClient);
        Path pdfPath = createPdf("Plenty of extractable text on the first page.", "");

        try {
            ExtractedDocument extractedDocument = service.extract(DocumentVersion.builder()
                    .filePath(pdfPath.toString())
                    .fileName("mixed.pdf")
                    .mimeType("application/pdf")
                    .build());

            assertFalse(service.shouldRunOcr(extractedDocument.getPages().get(0).getOriginalText()));
            assertTrue(extractedDocument.getPages().get(1).getOcrApplied());
            assertEquals("OCR text for blank page", extractedDocument.getPages().get(1).getExtractedText());
            assertEquals("", extractedDocument.getPages().get(1).getOriginalText());
            assertEquals("OCR text for blank page", extractedDocument.getPages().get(1).getOcrText());
            verify(ocrClient).performOcr(any());
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void preservesOriginalTextWhenOcrAddsContentToLowTextPage() throws IOException {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        when(ocrClient.performOcr(any())).thenReturn(DocumentPageOcrResult.builder()
                .success(true)
                .text("Recovered OCR text with enough characters")
                .confidence(0.91d)
                .build());
        DocumentTextExtractionService service = newService(ocrClient);
        Path pdfPath = createPdf("tiny", "Normal full text on page two.");

        try {
            ExtractedDocument extractedDocument = service.extract(DocumentVersion.builder()
                    .filePath(pdfPath.toString())
                    .fileName("low-text.pdf")
                    .mimeType("application/pdf")
                    .build());

            assertTrue(extractedDocument.getPages().get(0).getExtractedText().contains("tiny"));
            assertTrue(extractedDocument.getPages().get(0).getExtractedText().contains("Recovered OCR text"));
            assertEquals("tiny", extractedDocument.getPages().get(0).getOriginalText());
            assertTrue(Boolean.TRUE.equals(extractedDocument.getPages().get(0).getOcrApplied()));
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void buildsWarningWhenOcrFails() throws IOException {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        when(ocrClient.performOcr(any())).thenReturn(DocumentPageOcrResult.builder()
                .success(false)
                .text("")
                .confidence(0.0d)
                .errorMessage("OCR service unavailable")
                .build());
        DocumentTextExtractionService service = newService(ocrClient);
        Path pdfPath = createPdf("", "This page contains plenty of normal text.");

        try {
            ExtractedDocument extractedDocument = service.extract(DocumentVersion.builder()
                    .filePath(pdfPath.toString())
                    .fileName("ocr-fail.pdf")
                    .mimeType("application/pdf")
                    .build());

            List<String> warnings = service.buildWarnings(extractedDocument);

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("OCR failed on page 1"));
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void buildsWarningWhenOcrProducesNoUsableText() throws IOException {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        when(ocrClient.performOcr(any())).thenReturn(DocumentPageOcrResult.builder()
                .success(true)
                .text("short")
                .confidence(0.5d)
                .build());
        DocumentTextExtractionService service = newService(ocrClient);
        Path pdfPath = createPdf("", "This page contains plenty of normal text.");

        try {
            ExtractedDocument extractedDocument = service.extract(DocumentVersion.builder()
                    .filePath(pdfPath.toString())
                    .fileName("ocr-empty.pdf")
                    .mimeType("application/pdf")
                    .build());

            List<String> warnings = service.buildWarnings(extractedDocument);

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("OCR produced no usable text on page 1"));
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void extractsTxtAsSinglePageWithoutOcr() throws IOException {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        DocumentTextExtractionService service = newService(ocrClient);
        Path txtPath = Files.createTempFile("kv-text-", ".txt");
        Files.writeString(txtPath, "Line one\nLine two", StandardCharsets.UTF_8);

        try {
            ExtractedDocument extractedDocument = service.extract(DocumentVersion.builder()
                    .filePath(txtPath.toString())
                    .fileName("sample.txt")
                    .mimeType("text/plain")
                    .build());

            assertEquals("TXT", extractedDocument.getExtractorType());
            assertEquals(1, extractedDocument.getPageCount());
            assertEquals("Line one\nLine two", extractedDocument.getPages().get(0).getExtractedText());
            assertFalse(Boolean.TRUE.equals(extractedDocument.getPages().get(0).getOcrApplied()));
            verify(ocrClient, never()).performOcr(any());
        } finally {
            Files.deleteIfExists(txtPath);
        }
    }

    @Test
    void failsWhenTextExtractionCannotReadFile() {
        DocumentPageOcrClient ocrClient = mock(DocumentPageOcrClient.class);
        DocumentTextExtractionService service = newService(ocrClient);

        DocumentTextExtractionException exception = assertThrows(
                DocumentTextExtractionException.class,
                () -> service.extract(DocumentVersion.builder()
                        .filePath(Path.of("missing-file.txt").toAbsolutePath().toString())
                        .fileName("missing-file.txt")
                        .mimeType("text/plain")
                        .build())
        );

        assertEquals(DocumentTextExtractionService.EXTRACTION_FAILED_ERROR_CODE, exception.getErrorCode());
    }

    private DocumentTextExtractionService newService(DocumentPageOcrClient ocrClient) {
        IngestionOcrProperties properties = new IngestionOcrProperties();
        properties.setEnabled(true);
        properties.setLowTextThreshold(20);
        properties.setRenderDpi(72);
        properties.setImageFormat("png");
        properties.setLanguages(List.of("eng"));
        return new DocumentTextExtractionService(ocrClient, properties);
    }

    private Path createPdf(String firstPageText, String secondPageText) throws IOException {
        Path pdfPath = Files.createTempFile("kv-pdf-", ".pdf");

        try (PDDocument document = new PDDocument()) {
            PDPage firstPage = new PDPage();
            PDPage secondPage = new PDPage();
            document.addPage(firstPage);
            document.addPage(secondPage);

            if (firstPageText != null && !firstPageText.isBlank()) {
                try (PDPageContentStream firstStream = new PDPageContentStream(document, firstPage)) {
                    firstStream.beginText();
                    firstStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    firstStream.newLineAtOffset(50, 700);
                    firstStream.showText(firstPageText);
                    firstStream.endText();
                }
            }

            if (secondPageText != null && !secondPageText.isBlank()) {
                try (PDPageContentStream secondStream = new PDPageContentStream(document, secondPage)) {
                    secondStream.beginText();
                    secondStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    secondStream.newLineAtOffset(50, 700);
                    secondStream.showText(secondPageText);
                    secondStream.endText();
                }
            }

            document.save(pdfPath.toFile());
        }

        return pdfPath;
    }
}
