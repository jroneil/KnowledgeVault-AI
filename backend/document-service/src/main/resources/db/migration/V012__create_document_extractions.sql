-- V012__create_document_extractions.sql
-- Adds durable page-aware extracted text storage for document versions.

CREATE TABLE document_extractions (
    id BIGSERIAL PRIMARY KEY,
    ingestion_job_id BIGINT NOT NULL REFERENCES ingestion_jobs(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id BIGINT NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    extractor_type VARCHAR(32) NOT NULL,
    page_count INT NOT NULL DEFAULT 0,
    character_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_extractions_version UNIQUE (version_id)
);

ALTER TABLE document_extractions ADD CONSTRAINT chk_document_extractions_page_count
    CHECK (page_count >= 0);

ALTER TABLE document_extractions ADD CONSTRAINT chk_document_extractions_character_count
    CHECK (character_count >= 0);

CREATE TABLE document_extraction_pages (
    id BIGSERIAL PRIMARY KEY,
    extraction_id BIGINT NOT NULL REFERENCES document_extractions(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    extracted_text TEXT NOT NULL,
    character_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_extraction_pages UNIQUE (extraction_id, page_number)
);

ALTER TABLE document_extraction_pages ADD CONSTRAINT chk_document_extraction_pages_page_number
    CHECK (page_number >= 1);

ALTER TABLE document_extraction_pages ADD CONSTRAINT chk_document_extraction_pages_character_count
    CHECK (character_count >= 0);

CREATE INDEX idx_document_extractions_document_id ON document_extractions(document_id);
CREATE INDEX idx_document_extractions_job_id ON document_extractions(ingestion_job_id);
CREATE INDEX idx_document_extraction_pages_extraction_id ON document_extraction_pages(extraction_id, page_number);

CREATE TRIGGER update_document_extractions_updated_at
    BEFORE UPDATE ON document_extractions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE document_extractions IS 'Durable extracted text summary for a document version';
COMMENT ON TABLE document_extraction_pages IS 'Page-aware extracted text content for a document extraction';
