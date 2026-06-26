-- V008__add_ingestion_job_uniqueness.sql
-- Ensures one durable ingestion job record per document/version pair.

ALTER TABLE ingestion_jobs
    ADD CONSTRAINT uq_ingestion_jobs_document_version UNIQUE (document_id, version_id);
