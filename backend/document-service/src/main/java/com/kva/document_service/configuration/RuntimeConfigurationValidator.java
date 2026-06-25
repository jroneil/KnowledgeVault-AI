package com.kva.document_service.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeConfigurationValidator {

    private static final String DEVELOPMENT_JWT_SECRET =
            "development-only-jwt-key-change-me-minimum-256-bits";
    private static final String DEVELOPMENT_INTERNAL_API_KEY =
            "development-only-change-me";

    private final String environment;
    private final String jwtSecret;
    private final String internalApiKey;

    public RuntimeConfigurationValidator(
            @Value("${app.environment}") String environment,
            @Value("${spring.security.jwt.secret}") String jwtSecret,
            @Value("${ai-service.internal-api-key}") String internalApiKey) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.internalApiKey = internalApiKey;
    }

    @PostConstruct
    void validate() {
        if (!"production".equalsIgnoreCase(environment)) {
            return;
        }

        if (DEVELOPMENT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be configured in production");
        }
        if (DEVELOPMENT_INTERNAL_API_KEY.equals(internalApiKey)) {
            throw new IllegalStateException("INTERNAL_API_KEY must be configured in production");
        }
    }
}
