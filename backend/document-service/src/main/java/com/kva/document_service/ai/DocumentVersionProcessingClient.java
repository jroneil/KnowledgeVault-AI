package com.kva.document_service.ai;

import com.kva.document_service.ai.dto.DocumentVersionProcessingRequest;
import com.kva.document_service.ai.dto.DocumentVersionProcessingResponse;

public interface DocumentVersionProcessingClient {

    DocumentVersionProcessingResponse processDocumentVersion(DocumentVersionProcessingRequest request);
}
