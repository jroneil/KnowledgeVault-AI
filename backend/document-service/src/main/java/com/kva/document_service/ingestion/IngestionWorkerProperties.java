package com.kva.document_service.ingestion;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "ingestion.worker")
public class IngestionWorkerProperties {
    private boolean enabled = false;
    private int batchSize = 5;
    private int maxAttempts = 3;
    private Duration fixedDelay = Duration.ofSeconds(30);
    private Duration initialDelay = Duration.ofSeconds(10);
    private Duration staleThreshold = Duration.ofMinutes(10);
    private Duration retryInitialDelay = Duration.ofSeconds(30);
    private double retryBackoffMultiplier = 2.0d;
    private Duration retryMaxDelay = Duration.ofMinutes(15);
}
