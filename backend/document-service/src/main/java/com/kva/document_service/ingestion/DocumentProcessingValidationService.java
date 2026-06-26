package com.kva.document_service.ingestion;

import com.kva.document_service.documents.DocumentVersion;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentProcessingValidationService {

    static final String UNSUPPORTED_FILE_TYPE_ERROR_CODE = "UNSUPPORTED_FILE_TYPE";
    static final String ENCRYPTED_DOCUMENT_ERROR_CODE = "ENCRYPTED_DOCUMENT";

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/html",
            "text/csv",
            "text/markdown"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "docx", "txt", "html", "htm", "csv", "md"
    );
    private static final byte[] OLE2_HEADER = new byte[] {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    public DocumentProcessingValidationResult validate(DocumentVersion version) {
        String filename = normalize(version.getFileName());
        String extension = extensionOf(filename);
        String mimeType = normalize(version.getMimeType());

        if (!isSupportedMimeType(mimeType) || !isSupportedExtension(extension)) {
            return DocumentProcessingValidationResult.builder()
                    .valid(false)
                    .errorCode(UNSUPPORTED_FILE_TYPE_ERROR_CODE)
                    .errorMessage("Unsupported document type for ingestion: "
                            + describeType(mimeType, filename))
                    .build();
        }

        Path filePath = safePath(version.getFilePath());
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return DocumentProcessingValidationResult.builder()
                    .valid(true)
                    .build();
        }

        try {
            if ("pdf".equals(extension) && containsPdfEncryptionMarker(filePath)) {
                return DocumentProcessingValidationResult.builder()
                        .valid(false)
                        .errorCode(ENCRYPTED_DOCUMENT_ERROR_CODE)
                        .errorMessage("Encrypted or password-protected PDF documents are not supported")
                        .build();
            }

            if ("docx".equals(extension) && hasOle2CompoundHeader(filePath)) {
                return DocumentProcessingValidationResult.builder()
                        .valid(false)
                        .errorCode(ENCRYPTED_DOCUMENT_ERROR_CODE)
                        .errorMessage("Encrypted or password-protected Word documents are not supported")
                        .build();
            }
        } catch (IOException ignored) {
            return DocumentProcessingValidationResult.builder()
                    .valid(true)
                    .build();
        }

        return DocumentProcessingValidationResult.builder()
                .valid(true)
                .build();
    }

    private boolean isSupportedMimeType(String mimeType) {
        return mimeType != null && SUPPORTED_MIME_TYPES.contains(mimeType);
    }

    private boolean isSupportedExtension(String extension) {
        return extension != null && SUPPORTED_EXTENSIONS.contains(extension);
    }

    private boolean containsPdfEncryptionMarker(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.ISO_8859_1);
        return content.contains("/Encrypt");
    }

    private boolean hasOle2CompoundHeader(Path filePath) throws IOException {
        byte[] fileHeader = Files.readAllBytes(filePath);
        if (fileHeader.length < OLE2_HEADER.length) {
            return false;
        }

        for (int index = 0; index < OLE2_HEADER.length; index++) {
            if (fileHeader[index] != OLE2_HEADER[index]) {
                return false;
            }
        }

        return true;
    }

    private String describeType(String mimeType, String filename) {
        return List.of(mimeType, filename)
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " (" + right + ")")
                .orElse("unknown");
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Path safePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return Path.of(filePath);
    }
}
