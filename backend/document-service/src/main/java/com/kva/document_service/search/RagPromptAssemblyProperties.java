package com.kva.document_service.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.prompt")
@Getter
@Setter
public class RagPromptAssemblyProperties {

    private int maxContextTokens = 2000;
}
