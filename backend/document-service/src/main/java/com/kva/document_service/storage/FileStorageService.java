package com.kva.document_service.storage;

import com.kva.document_service.configuration.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "txt", "html", "htm", "csv", "md"
    );
    private static final Duration ABANDONED_UPLOAD_MAX_AGE = Duration.ofHours(24);

    private final FileStorageProperties properties;
    private Path rootLocation;

    /**
     * Initialize storage directories after dependency injection
     */
    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(properties.getBasePath());

        try {
            Files.createDirectories(rootLocation);
            Files.createDirectories(rootLocation.resolve("originals"));
            Files.createDirectories(rootLocation.resolve("uploads"));
            Files.createDirectories(rootLocation.resolve("archived"));
            cleanupAbandonedUploads(ABANDONED_UPLOAD_MAX_AGE);

            log.info("File storage initialized at: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage location", e);
        }
    }

    /**
     * Store original file for a document
     *
     * @param file The multipart file to store
     * @param collectionId The collection ID
     * @param documentId The document ID
     * @param version The version number
     * @return The full path where the file was stored
     */
    public String storeOriginalFile(MultipartFile file, Long collectionId,
                                   Long documentId, int version) {
        validateFile(file);

        try {
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new StorageException("Invalid file name");
            }

            Path destinationPath = getFileStoragePath(collectionId, documentId,
                                                       version, originalFilename);
            Files.createDirectories(destinationPath.getParent());

            log.info("Storing file: {} to {}", originalFilename, destinationPath);

            Files.copy(file.getInputStream(), destinationPath,
                      StandardCopyOption.REPLACE_EXISTING);

            log.info("File stored successfully: {}", destinationPath);
            return destinationPath.toString();

        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * Get the storage path for a file
     *
     * @param collectionId The collection ID
     * @param documentId The document ID
     * @param version The version number
     * @param filename The original filename
     * @return The full path where the file should be stored
     */
    public Path getFileStoragePath(Long collectionId, Long documentId,
                                   int version, String filename) {
        // Create path: originals/{collection_id}/{document_id}/v{version}_{filename}
        String safeFilename = sanitizeFilename(filename);
        String extension = extensionOf(safeFilename);
        String versionedFilename = "v" + version + "_" + UUID.randomUUID() +
                (extension.isEmpty() ? "" : "." + extension);
        return rootLocation
            .resolve("originals")
            .resolve(collectionId.toString())
            .resolve(documentId.toString())
            .resolve(versionedFilename);
    }

    /**
     * Load a file as a Resource
     *
     * @param filePath The file path to load
     * @return The file as a Resource
     */
    public Resource loadFile(String filePath) {
        try {
            Path file = resolveStoredPath(filePath);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.debug("File loaded successfully: {}", filePath);
                return resource;
            }

            throw new StorageFileNotFoundException("Could not read file: " + filePath);

        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filePath, e);
        }
    }

    /**
     * Delete all files for a specific document
     *
     * @param collectionId The collection ID
     * @param documentId The document ID
     */
    public void deleteDocumentFiles(Long collectionId, Long documentId) {
        try {
            Path documentPath = rootLocation
                .resolve("originals")
                .resolve(collectionId.toString())
                .resolve(documentId.toString());

            if (Files.exists(documentPath)) {
                try (Stream<Path> paths = Files.walk(documentPath)) {
                    paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (file.delete()) {
                                log.debug("Deleted file: {}", file);
                            } else {
                                log.warn("Failed to delete file: {}", file);
                            }
                        });
                }

                log.info("Deleted all files for document {} in collection {}",
                        documentId, collectionId);
            } else {
                log.warn("Document path does not exist: {}", documentPath);
            }

        } catch (IOException e) {
            log.error("Could not delete files for document {}", documentId, e);
            throw new StorageException("Failed to delete document files", e);
        }
    }

    /**
     * Delete a specific file version
     *
     * @param filePath The file path to delete
     */
    public void deleteFile(String filePath) {
        try {
            Path file = resolveStoredPath(filePath);
            if (Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted file: {}", filePath);
            } else {
                log.warn("File does not exist: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Could not delete file: {}", filePath, e);
            throw new StorageException("Failed to delete file", e);
        }
    }

    /**
     * Check if a file exists
     *
     * @param filePath The file path to check
     * @return true if the file exists, false otherwise
     */
    public boolean fileExists(String filePath) {
        Path file = resolveStoredPath(filePath);
        return Files.exists(file) && Files.isReadable(file);
    }

    /**
     * Get the size of a file in bytes
     *
     * @param filePath The file path
     * @return The file size in bytes
     */
    public long getFileSize(String filePath) {
        try {
            Path file = resolveStoredPath(filePath);
            if (Files.exists(file)) {
                return Files.size(file);
            }
            return 0;
        } catch (IOException e) {
            log.error("Could not get file size: {}", filePath, e);
            return 0;
        }
    }

    /**
     * Validate file according to storage properties
     *
     * @param file The multipart file to validate
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("Cannot store empty file");
        }

        if (file.getSize() > properties.getMaxFileSize()) {
            throw new StorageException("File size exceeds maximum allowed size of " +
                                      (properties.getMaxFileSize() / 1024 / 1024) + "MB");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new StorageException("File extension not allowed: " + extension);
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !properties.getAllowedTypes().contains(mimeType)) {
            throw new StorageException("File type not allowed: " + mimeType +
                                      ". Allowed types: " + properties.getAllowedTypes());
        }
    }

    /**
     * Sanitize filename to prevent path traversal and other security issues
     *
     * @param filename The original filename
     * @return The sanitized filename
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) {
            throw new StorageException("Invalid file name");
        }

        String sanitized = Paths.get(filename).getFileName().toString();
        sanitized = sanitized.replaceAll("[/\\\\]", "");

        // Remove any null characters
        sanitized = sanitized.replaceAll("\0", "");

        // Limit filename length to prevent issues
        if (sanitized.length() > 255) {
            String extension = "";
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, Math.min(255 - extension.length(), lastDot)) + extension;
            } else {
                sanitized = sanitized.substring(0, 255);
            }
        }

        sanitized = sanitized.trim();
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            throw new StorageException("Invalid file name");
        }
        return sanitized;
    }

    private String extensionOf(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot < 0 || lastDot == filename.length() - 1
                ? ""
                : filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private Path resolveStoredPath(String filePath) {
        Path resolved = Paths.get(filePath).toAbsolutePath().normalize();
        Path root = rootLocation.toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("File path is outside the configured storage root");
        }
        return resolved;
    }

    /**
     * Get the base storage path
     *
     * @return The base storage path
     */
    public Path getRootLocation() {
        return rootLocation;
    }

    public void cleanupAbandonedUploads(Duration maxAge) {
        Path uploadsRoot = rootLocation.resolve("uploads").toAbsolutePath().normalize();
        Instant cutoff = Instant.now().minus(maxAge);

        try (Stream<Path> paths = Files.walk(uploadsRoot)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(uploadsRoot))
                    .forEach(path -> deleteAbandonedUploadPath(path, cutoff));
        } catch (IOException e) {
            throw new StorageException("Failed to clean up abandoned uploads", e);
        }
    }

    /**
     * Get storage statistics
     *
     * @return Map containing storage statistics
     */
    public java.util.Map<String, Object> getStorageStats() {
        try (Stream<Path> sizePaths = Files.walk(rootLocation);
             Stream<Path> countPaths = Files.walk(rootLocation)) {
            long totalSize = sizePaths
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();

            long fileCount = countPaths
                .filter(Files::isRegularFile)
                .count();

            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("basePath", rootLocation.toAbsolutePath());
            stats.put("totalSizeBytes", totalSize);
            stats.put("totalSizeMB", totalSize / 1024 / 1024);
            stats.put("fileCount", fileCount);
            stats.put("maxFileSizeMB", properties.getMaxFileSize() / 1024 / 1024);
            stats.put("allowedTypes", properties.getAllowedTypes());

            return stats;

        } catch (IOException e) {
            log.error("Could not calculate storage stats", e);
            return java.util.Map.of("error", "Could not calculate storage stats");
        }
    }

    private void deleteAbandonedUploadPath(Path path, Instant cutoff) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> children = Files.list(path)) {
                    if (children.findAny().isEmpty()) {
                        Files.delete(path);
                    }
                }
                return;
            }

            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                Files.deleteIfExists(path);
                log.info("Deleted abandoned upload: {}", path);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up upload path: {}", path, e);
        }
    }
}
