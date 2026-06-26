package com.kva.document_service.ingestion;

import com.kva.document_service.documents.DocumentVersion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentTextExtractionService {

    static final String EXTRACTION_FAILED_ERROR_CODE = "TEXT_EXTRACTION_FAILED";
    static final String EXTRACTOR_NOT_SUPPORTED_ERROR_CODE = "TEXT_EXTRACTION_NOT_SUPPORTED";

    private final DocumentPageOcrClient documentPageOcrClient;
    private final IngestionOcrProperties ingestionOcrProperties;

    public DocumentTextExtractionService(DocumentPageOcrClient documentPageOcrClient,
                                         IngestionOcrProperties ingestionOcrProperties) {
        this.documentPageOcrClient = documentPageOcrClient;
        this.ingestionOcrProperties = ingestionOcrProperties;
    }

    public ExtractedDocument extract(DocumentVersion version) {
        String extension = extensionOf(version.getFileName());

        return switch (extension) {
            case "pdf" -> extractPdf(version);
            case "txt" -> extractTextFile(version);
            default -> throw new DocumentTextExtractionException(
                    EXTRACTOR_NOT_SUPPORTED_ERROR_CODE,
                    "Text extraction is not yet implemented for file: " + version.getFileName()
            );
        };
    }

    public List<String> buildWarnings(ExtractedDocument extractedDocument) {
        List<String> warnings = new ArrayList<>();

        for (ExtractedDocumentPage page : extractedDocument.getPages()) {
            if (Boolean.TRUE.equals(page.getOcrApplied()) && page.getOcrText() == null) {
                warnings.add("OCR failed on page " + page.getPageNumber());
                continue;
            }

            if (Boolean.TRUE.equals(page.getOcrApplied()) && !hasUsableText(page.getOcrText())) {
                warnings.add("OCR produced no usable text on page " + page.getPageNumber());
                continue;
            }

            String normalized = normalize(page.getExtractedText());
            if (normalized == null || normalized.length() < ingestionOcrProperties.getLowTextThreshold()) {
                warnings.add("Low or no extractable text detected on page " + page.getPageNumber());
            }
        }

        return warnings;
    }

    private ExtractedDocument extractPdf(DocumentVersion version) {
        Path path = requirePath(version.getFilePath());

        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<ExtractedDocumentPage> pages = new ArrayList<>();
            int totalCharacters = 0;

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String originalText = normalizePageText(stripper.getText(document));
                String finalText = originalText;
                String ocrText = null;
                boolean ocrApplied = false;

                if (shouldRunOcr(originalText)) {
                    ocrApplied = true;
                    DocumentPageOcrResult ocrResult = performOcr(renderer, pageNumber);
                    if (ocrResult != null && ocrResult.isSuccess()) {
                        ocrText = normalizePageText(ocrResult.getText());
                        if (hasUsableText(ocrText)) {
                            finalText = normalize(originalText) != null
                                    ? normalizePageText(originalText + "\n\n" + ocrText)
                                    : ocrText;
                        }
                    }
                }

                int characterCount = finalText.length();
                totalCharacters += characterCount;

                pages.add(ExtractedDocumentPage.builder()
                        .pageNumber(pageNumber)
                        .extractedText(finalText)
                        .originalText(originalText)
                        .ocrText(ocrText)
                        .ocrApplied(ocrApplied)
                        .characterCount(characterCount)
                        .build());
            }

            return ExtractedDocument.builder()
                    .extractorType("PDF")
                    .pageCount(pages.size())
                    .characterCount(totalCharacters)
                    .pages(pages)
                    .build();
        } catch (IOException exception) {
            throw new DocumentTextExtractionException(
                    EXTRACTION_FAILED_ERROR_CODE,
                    "Failed to extract text from PDF document",
                    exception
            );
        }
    }

    private ExtractedDocument extractTextFile(DocumentVersion version) {
        Path path = requirePath(version.getFilePath());

        try {
            String text = normalizePageText(Files.readString(path, StandardCharsets.UTF_8));
            return ExtractedDocument.builder()
                    .extractorType("TXT")
                    .pageCount(1)
                    .characterCount(text.length())
                    .pages(List.of(ExtractedDocumentPage.builder()
                            .pageNumber(1)
                            .extractedText(text)
                            .originalText(text)
                            .ocrText(null)
                            .ocrApplied(false)
                            .characterCount(text.length())
                            .build()))
                    .build();
        } catch (IOException exception) {
            throw new DocumentTextExtractionException(
                    EXTRACTION_FAILED_ERROR_CODE,
                    "Failed to extract text from text document",
                    exception
            );
        }
    }

    private DocumentPageOcrResult performOcr(PDFRenderer renderer, int pageNumber) {
        Path imagePath = null;
        try {
            imagePath = Files.createTempFile("kv-ocr-page-" + pageNumber + "-", "." + ingestionOcrProperties.getImageFormat());
            ImageIO.write(
                    renderer.renderImageWithDPI(pageNumber - 1, ingestionOcrProperties.getRenderDpi(), ImageType.RGB),
                    ingestionOcrProperties.getImageFormat(),
                    imagePath.toFile()
            );
            DocumentPageOcrResult result = documentPageOcrClient.performOcr(imagePath);
            if (result != null) {
                return result;
            }
            return DocumentPageOcrResult.builder()
                    .success(false)
                    .text("")
                    .confidence(0.0d)
                    .errorMessage("OCR client returned no result")
                    .build();
        } catch (IOException exception) {
            return DocumentPageOcrResult.builder()
                    .success(false)
                    .text("")
                    .confidence(0.0d)
                    .errorMessage(exception.getMessage())
                    .build();
        } finally {
            if (imagePath != null) {
                try {
                    Files.deleteIfExists(imagePath);
                } catch (IOException ignored) {
                    // best effort temp cleanup
                }
            }
        }
    }

    boolean shouldRunOcr(String extractedText) {
        if (!ingestionOcrProperties.isEnabled()) {
            return false;
        }
        String normalized = normalize(extractedText);
        return normalized == null || normalized.length() < ingestionOcrProperties.getLowTextThreshold();
    }

    private boolean hasUsableText(String text) {
        String normalized = normalize(text);
        return normalized != null && normalized.length() >= ingestionOcrProperties.getLowTextThreshold();
    }

    private Path requirePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new DocumentTextExtractionException(
                    EXTRACTION_FAILED_ERROR_CODE,
                    "Document version does not have a valid file path"
            );
        }
        return Path.of(filePath);
    }

    private String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizePageText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
