-- V013__add_document_extraction_ocr_fields.sql
-- Adds durable OCR metadata for extracted pages.

ALTER TABLE document_extraction_pages
    ADD COLUMN original_text TEXT,
    ADD COLUMN ocr_text TEXT,
    ADD COLUMN ocr_applied BOOLEAN NOT NULL DEFAULT FALSE;
