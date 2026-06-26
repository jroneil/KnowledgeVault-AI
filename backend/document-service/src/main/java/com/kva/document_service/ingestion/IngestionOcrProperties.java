package com.kva.document_service.ingestion;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "ingestion.ocr")
public class IngestionOcrProperties {
    private boolean enabled = true;
    private int lowTextThreshold = 20;
    private int renderDpi = 300;
    private String imageFormat = "png";
    private List<String> languages = List.of("eng");
}
