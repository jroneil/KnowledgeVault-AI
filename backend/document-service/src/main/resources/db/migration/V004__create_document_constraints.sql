-- V004__create_document_constraints.sql
-- Adds constraints to ensure data integrity and business rules
-- This migration completes the database schema for Phase 2

-- Check constraints for document status
ALTER TABLE documents ADD CONSTRAINT chk_status
    CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED'));

-- Check constraints for document versions
ALTER TABLE document_versions ADD CONSTRAINT chk_version_number
    CHECK (version_number > 0);

ALTER TABLE document_versions ADD CONSTRAINT chk_file_size
    CHECK (file_size >= 0);

-- Ensure only one version can be current per document
CREATE UNIQUE INDEX idx_doc_versions_current_unique
    ON document_versions(document_id, is_current)
    WHERE is_current = true;

-- Add constraint to ensure current_version is never negative
ALTER TABLE documents ADD CONSTRAINT chk_current_version
    CHECK (current_version >= 1);

-- Comments for constraints
COMMENT ON CONSTRAINT chk_status ON documents IS 'Ensures only valid status values';
COMMENT ON CONSTRAINT chk_version_number ON document_versions IS 'Version numbers must be positive';
COMMENT ON CONSTRAINT chk_file_size ON document_versions IS 'File sizes cannot be negative';
COMMENT ON CONSTRAINT chk_current_version ON documents IS 'Current version must be at least 1';

-- Add helpful views for common queries
CREATE VIEW active_documents_view AS
SELECT
    d.id,
    d.collection_id,
    c.name as collection_name,
    d.title,
    d.description,
    d.status,
    d.current_version,
    d.created_by,
    u.username as created_by_username,
    d.created_at,
    d.updated_at,
    dv.file_name,
    dv.file_size,
    dv.upload_date
FROM documents d
JOIN collections c ON d.collection_id = c.id
JOIN users u ON d.created_by = u.id
JOIN document_versions dv ON d.id = dv.document_id AND dv.is_current = true
WHERE d.status = 'ACTIVE' AND c.is_active = true;

COMMENT ON VIEW active_documents_view IS 'Convenient view of active documents with current version info';

-- Create view for document search (includes metadata)
CREATE VIEW document_search_view AS
SELECT
    d.id,
    d.collection_id,
    c.name as collection_name,
    d.title,
    d.description,
    d.status,
    d.current_version,
    d.created_at,
    dm.product,
    dm.revision,
    dm.department,
    dm.manufacturer,
    dm.tags,
    dm.category,
    dm.effective_date,
    dv.file_name,
    dv.file_size,
    u.username as created_by_username
FROM documents d
JOIN collections c ON d.collection_id = c.id
LEFT JOIN document_metadata dm ON d.id = dm.document_id
LEFT JOIN document_versions dv ON d.id = dv.document_id AND dv.is_current = true
LEFT JOIN users u ON d.created_by = u.id;

COMMENT ON VIEW document_search_view IS 'Comprehensive view for document search with all metadata';