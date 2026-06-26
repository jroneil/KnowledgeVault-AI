package com.kva.document_service.ingestion;

import java.nio.file.Path;

public interface DocumentPageOcrClient {

    DocumentPageOcrResult performOcr(Path imagePath);
}
