package com.kva.document_service.ingestion;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ingestion.chunking")
public class IngestionChunkingProperties {
    private boolean enabled = true;
    private int chunkSize = 1000;
    private int overlap = 200;
}
