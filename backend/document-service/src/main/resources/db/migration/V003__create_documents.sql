-- V003__create_documents.sql
-- Creates documents, document_versions, and document_metadata tables
-- This migration establishes the core document management system

-- Documents table: Core business entity for document management
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT REFERENCES collections(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    current_version INT DEFAULT 1,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Document versions table: Track all versions of documents
CREATE TABLE document_versions (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_current BOOLEAN DEFAULT true,
    UNIQUE(document_id, version_number)
);

-- Document metadata table: Rich metadata for searchability
CREATE TABLE document_metadata (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    product VARCHAR(255),
    revision VARCHAR(50),
    department VARCHAR(255),
    manufacturer VARCHAR(255),
    tags TEXT[],
    category VARCHAR(255),
    effective_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id)
);

-- Performance indexes for documents
CREATE INDEX idx_documents_collection ON documents(collection_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_created_by ON documents(created_by);
CREATE INDEX idx_documents_created_at ON documents(created_at);
CREATE INDEX idx_documents_title ON documents USING GIN(to_tsvector('english', title));

-- Performance indexes for document_versions
CREATE INDEX idx_doc_versions_document ON document_versions(document_id);
CREATE INDEX idx_doc_versions_current ON document_versions(is_current);
CREATE INDEX idx_doc_versions_upload_date ON document_versions(upload_date);

-- Performance indexes for document_metadata
CREATE INDEX idx_metadata_product ON document_metadata(product);
CREATE INDEX idx_metadata_tags ON document_metadata USING GIN(tags);
CREATE INDEX idx_metadata_category ON document_metadata(category);
CREATE INDEX idx_metadata_department ON document_metadata(department);
CREATE INDEX idx_metadata_revision ON document_metadata(revision);

-- Full-text search index for document metadata
CREATE INDEX idx_metadata_fulltext ON document_metadata USING GIN(
    to_tsvector('english',
        COALESCE(product, '') || ' ' ||
        COALESCE(revision, '') || ' ' ||
        COALESCE(department, '') || ' ' ||
        COALESCE(manufacturer, '') || ' ' ||
        COALESCE(category, '')
    )
);

-- Triggers to automatically update updated_at timestamps
CREATE TRIGGER update_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_metadata_updated_at
    BEFORE UPDATE ON document_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for table documentation
COMMENT ON TABLE documents IS 'Core business entity for document management';
COMMENT ON TABLE document_versions IS 'Track all versions of documents with file storage info';
COMMENT ON TABLE document_metadata IS 'Rich metadata for advanced searchability';

COMMENT ON COLUMN documents.collection_id IS 'Collection this document belongs to';
COMMENT ON COLUMN documents.status IS 'Document status: ACTIVE, ARCHIVED, DELETED';
COMMENT ON COLUMN documents.current_version IS 'Current version number of the document';

COMMENT ON COLUMN document_versions.version_number IS 'Version number (1, 2, 3, etc.)';
COMMENT ON COLUMN document_versions.file_path IS 'Full path to stored file in file system';
COMMENT ON COLUMN document_versions.is_current IS 'Whether this is the current/active version';

COMMENT ON COLUMN document_metadata.tags IS 'Array of tags for flexible categorization';
COMMENT ON COLUMN document_metadata.effective_date IS 'Date when document content becomes effective';