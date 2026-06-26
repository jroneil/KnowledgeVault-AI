-- V007__create_ingestion_jobs.sql
-- Adds durable ingestion job tracking for document version processing.

CREATE TABLE ingestion_jobs (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id BIGINT NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    embedding_model VARCHAR(255),
    llm_model VARCHAR(255),
    chunk_count INT NOT NULL DEFAULT 0,
    embedding_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ingestion_jobs ADD CONSTRAINT chk_ingestion_job_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'INDEXED', 'FAILED', 'CANCELLED'));

ALTER TABLE ingestion_jobs ADD CONSTRAINT chk_ingestion_job_progress
    CHECK (progress_percent >= 0 AND progress_percent <= 100);

ALTER TABLE ingestion_jobs ADD CONSTRAINT chk_ingestion_job_attempt_count
    CHECK (attempt_count >= 0);

ALTER TABLE ingestion_jobs ADD CONSTRAINT chk_ingestion_job_chunk_count
    CHECK (chunk_count >= 0);

ALTER TABLE ingestion_jobs ADD CONSTRAINT chk_ingestion_job_embedding_count
    CHECK (embedding_count >= 0);

CREATE INDEX idx_ingestion_jobs_document_id ON ingestion_jobs(document_id);
CREATE INDEX idx_ingestion_jobs_version_id ON ingestion_jobs(version_id);
CREATE INDEX idx_ingestion_jobs_status ON ingestion_jobs(status);
CREATE INDEX idx_ingestion_jobs_created_at ON ingestion_jobs(created_at DESC);

CREATE TRIGGER update_ingestion_jobs_updated_at
    BEFORE UPDATE ON ingestion_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE ingestion_jobs IS 'Durable ingestion job state for document version processing';
COMMENT ON COLUMN ingestion_jobs.status IS 'Job status: PENDING, PROCESSING, INDEXED, FAILED, CANCELLED';
COMMENT ON COLUMN ingestion_jobs.progress_percent IS 'Progress percentage from 0 to 100';
COMMENT ON COLUMN ingestion_jobs.attempt_count IS 'Number of processing attempts recorded for this job';
