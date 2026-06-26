package com.kva.document_service.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionRequestPayload {
    private List<ChatCompletionMessage> messages;
    private String model;
    private Double temperature;
    private Integer maxTokens;
}
