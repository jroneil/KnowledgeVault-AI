-- V009__add_ingestion_retry_fields.sql
-- Adds durable retry scheduling metadata for ingestion jobs.

ALTER TABLE ingestion_jobs
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN retryable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_error_code VARCHAR(100),
    ADD COLUMN last_error_message TEXT;

CREATE INDEX idx_ingestion_jobs_ready_pending
    ON ingestion_jobs(status, next_attempt_at, created_at, id);
