package com.kva.document_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "file-storage")
public class FileStorageProperties {
    private String basePath = "/storage/documents";
    private long maxFileSize = 52428800; // 50MB in bytes
    private List<String> allowedTypes = List.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/html",
        "text/csv",
        "text/markdown"
    );
}