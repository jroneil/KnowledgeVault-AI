package com.kva.document_service.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        IngestionWorkerProperties.class,
        IngestionOcrProperties.class,
        IngestionChunkingProperties.class,
        IngestionEmbeddingProperties.class
})
public class IngestionWorkerConfiguration {
}
