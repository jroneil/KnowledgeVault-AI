-- V010__add_ingestion_cancellation_fields.sql
-- Adds durable cancellation request tracking for ingestion jobs.

ALTER TABLE ingestion_jobs
    ADD COLUMN cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE;
