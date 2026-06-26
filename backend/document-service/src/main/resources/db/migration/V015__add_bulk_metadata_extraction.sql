-- Adds extended document metadata fields and durable metadata extraction results.

ALTER TABLE document_metadata
    ADD COLUMN IF NOT EXISTS model VARCHAR(255),
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(255),
    ADD COLUMN IF NOT EXISTS language VARCHAR(50),
    ADD COLUMN IF NOT EXISTS publication_date DATE,
    ADD COLUMN IF NOT EXISTS page_count INT;

ALTER TABLE document_metadata DROP CONSTRAINT IF EXISTS chk_document_metadata_page_count;
ALTER TABLE document_metadata ADD CONSTRAINT chk_document_metadata_page_count
    CHECK (page_count IS NULL OR page_count >= 0);

CREATE INDEX IF NOT EXISTS idx_metadata_model ON document_metadata(model);
CREATE INDEX IF NOT EXISTS idx_metadata_document_type ON document_metadata(document_type);
CREATE INDEX IF NOT EXISTS idx_metadata_document_number ON document_metadata(document_number);

CREATE TABLE IF NOT EXISTS document_metadata_extraction_results (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    extracted_title VARCHAR(500),
    extracted_manufacturer VARCHAR(255),
    extracted_model VARCHAR(255),
    extracted_document_type VARCHAR(255),
    extracted_document_number VARCHAR(255),
    extracted_revision VARCHAR(50),
    extracted_language VARCHAR(50),
    extracted_publication_date DATE,
    extracted_page_count INT,
    extracted_tags TEXT[],
    confidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_summary TEXT,
    needs_review BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_metadata_extraction_document UNIQUE (document_id)
);

ALTER TABLE document_metadata_extraction_results DROP CONSTRAINT IF EXISTS chk_document_metadata_extraction_page_count;
ALTER TABLE document_metadata_extraction_results ADD CONSTRAINT chk_document_metadata_extraction_page_count
    CHECK (extracted_page_count IS NULL OR extracted_page_count >= 0);

CREATE INDEX IF NOT EXISTS idx_metadata_extraction_document ON document_metadata_extraction_results(document_id);
CREATE INDEX IF NOT EXISTS idx_metadata_extraction_needs_review ON document_metadata_extraction_results(needs_review);

DROP TRIGGER IF EXISTS update_document_metadata_extraction_results_updated_at ON document_metadata_extraction_results;
CREATE TRIGGER update_document_metadata_extraction_results_updated_at
    BEFORE UPDATE ON document_metadata_extraction_results
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE document_metadata_extraction_results IS 'Stores extracted metadata candidates and confidence per document';
COMMENT ON COLUMN document_metadata.document_type IS 'Extracted or manually assigned document type';
COMMENT ON COLUMN document_metadata.document_number IS 'Document control number or identifier';
COMMENT ON COLUMN document_metadata.publication_date IS 'Publication date extracted from document metadata or content';
COMMENT ON COLUMN document_metadata.page_count IS 'Detected page count for the document';
