-- V011__create_ingestion_warnings.sql
-- Adds durable warning records for ingestion validation and extraction results.

CREATE TABLE ingestion_warnings (
    id BIGSERIAL PRIMARY KEY,
    ingestion_job_id BIGINT NOT NULL REFERENCES ingestion_jobs(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id BIGINT NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    warning_code VARCHAR(100),
    warning_message TEXT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ingestion_warnings ADD CONSTRAINT chk_ingestion_warning_severity
    CHECK (severity IN ('WARNING', 'ERROR'));

CREATE INDEX idx_ingestion_warnings_job_id
    ON ingestion_warnings(ingestion_job_id, created_at, id);

CREATE INDEX idx_ingestion_warnings_version_id
    ON ingestion_warnings(version_id, created_at, id);

COMMENT ON TABLE ingestion_warnings IS 'Durable warnings and validation messages produced during ingestion';
COMMENT ON COLUMN ingestion_warnings.warning_code IS 'Optional warning or validation code';
COMMENT ON COLUMN ingestion_warnings.severity IS 'WARNING or ERROR';
