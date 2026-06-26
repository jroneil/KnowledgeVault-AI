package com.kva.document_service.ingestion;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ingestion.embedding")
public class IngestionEmbeddingProperties {
    private boolean enabled = true;
    private String provider = "ai-service";
    private String modelName = "nomic-embed-text";
    private String modelVersion;
    private int dimension = 768;
    private int batchSize = 16;
    private long timeoutMs = 30000;
}
