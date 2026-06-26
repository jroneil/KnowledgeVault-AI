-- V014__add_ingestion_chunking_fields.sql
-- Adds ingestion job linkage and page range metadata to durable chunks.

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS ingestion_job_id BIGINT REFERENCES ingestion_jobs(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS source_page_from INT,
    ADD COLUMN IF NOT EXISTS source_page_to INT;

ALTER TABLE document_chunks DROP CONSTRAINT IF EXISTS chk_document_chunks_source_page_from;
ALTER TABLE document_chunks ADD CONSTRAINT chk_document_chunks_source_page_from
    CHECK (source_page_from IS NULL OR source_page_from >= 1);

ALTER TABLE document_chunks DROP CONSTRAINT IF EXISTS chk_document_chunks_source_page_to;
ALTER TABLE document_chunks ADD CONSTRAINT chk_document_chunks_source_page_to
    CHECK (source_page_to IS NULL OR source_page_to >= 1);

CREATE INDEX IF NOT EXISTS idx_chunks_ingestion_job ON document_chunks(ingestion_job_id);

COMMENT ON COLUMN document_chunks.ingestion_job_id IS 'Ingestion job that produced this chunk';
COMMENT ON COLUMN document_chunks.source_page_from IS 'First source page covered by the chunk';
COMMENT ON COLUMN document_chunks.source_page_to IS 'Last source page covered by the chunk';
