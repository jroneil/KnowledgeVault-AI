-- V002__create_collections.sql
-- Creates the collections table for organizing documents
-- This migration establishes the foundation for document organization

CREATE TABLE collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_collections_active ON collections(is_active);
CREATE INDEX idx_collections_created_by ON collections(created_by);

-- Trigger to automatically update updated_at timestamp
CREATE TRIGGER update_collections_updated_at
    BEFORE UPDATE ON collections
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comment for table documentation
COMMENT ON TABLE collections IS 'Logical groupings for organizing documents';
COMMENT ON COLUMN collections.name IS 'Unique name for the collection';
COMMENT ON COLUMN collections.is_active IS 'Whether the collection is active or archived';
COMMENT ON COLUMN collections.created_by IS 'User who created the collection (foreign key to users)';