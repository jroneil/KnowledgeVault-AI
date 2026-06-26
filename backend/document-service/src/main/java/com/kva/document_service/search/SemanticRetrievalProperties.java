package com.kva.document_service.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "semantic-retrieval")
@Getter
@Setter
public class SemanticRetrievalProperties {

    private int topK = 5;
}
