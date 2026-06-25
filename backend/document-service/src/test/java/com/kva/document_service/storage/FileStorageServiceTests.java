package com.kva.document_service.storage;

import com.kva.document_service.configuration.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTests {

    @TempDir
    Path tempDir;

    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        storageService = new FileStorageService(properties);
        storageService.init();
    }

    @Test
    void storesUploadsUnderConfiguredRootWithGeneratedName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../unsafe.txt",
                "text/plain",
                "phase one".getBytes()
        );

        String storedPath = storageService.storeOriginalFile(file, 10L, 20L, 1);
        Path stored = Path.of(storedPath);

        assertTrue(stored.toAbsolutePath().normalize().startsWith(tempDir.toAbsolutePath().normalize()));
        assertTrue(Files.exists(stored));
        assertTrue(stored.getFileName().toString().startsWith("v1_"));
        assertTrue(stored.getFileName().toString().endsWith(".txt"));
        assertEquals("phase one", Files.readString(stored));
    }

    @Test
    void rejectsDisallowedExtensionEvenWhenMimeTypeIsAllowed() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "text/plain",
                "not executable".getBytes()
        );

        assertThrows(StorageException.class,
                () -> storageService.storeOriginalFile(file, 10L, 20L, 1));
    }

    @Test
    void rejectsPathsOutsideConfiguredStorageRoot() {
        assertThrows(StorageException.class,
                () -> storageService.loadFile(tempDir.resolveSibling("outside.txt").toString()));
    }

    @Test
    void removesOnlyStaleFilesFromUploadTempArea() throws Exception {
        Path uploadsRoot = tempDir.resolve("uploads");
        Path staleDir = Files.createDirectories(uploadsRoot.resolve("stale"));
        Path freshDir = Files.createDirectories(uploadsRoot.resolve("fresh"));
        Path staleFile = Files.writeString(staleDir.resolve("old.tmp"), "stale");
        Path freshFile = Files.writeString(freshDir.resolve("new.tmp"), "fresh");

        Files.setLastModifiedTime(staleFile, FileTime.from(Instant.now().minus(Duration.ofHours(48))));
        Files.setLastModifiedTime(freshFile, FileTime.from(Instant.now()));

        storageService.cleanupAbandonedUploads(Duration.ofHours(24));

        assertTrue(Files.notExists(staleFile));
        assertTrue(Files.notExists(staleDir));
        assertTrue(Files.exists(freshFile));
        assertTrue(Files.exists(freshDir));
    }
}
