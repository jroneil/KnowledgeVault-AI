-- V005__create_chunks_embeddings.sql
-- Create tables for document chunks and vector embeddings
-- Supports semantic search and RAG capabilities

-- Document chunks table
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id BIGINT REFERENCES document_versions(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    page_number INT,
    section_name VARCHAR(255),
    token_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_token_count CHECK (token_count >= 0),
    UNIQUE(document_id, version_id, chunk_index)
);

-- Vector embeddings table
CREATE TABLE IF NOT EXISTS embeddings (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50),
    embedding vector(1024) NOT NULL, -- Default dimension for nomic-embed-text
    dimension INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dimension CHECK (dimension > 0),
    UNIQUE(chunk_id, model_name)
);

-- Indexes for document chunks
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_version ON document_chunks(version_id);
CREATE INDEX IF NOT EXISTS idx_chunks_created_at ON document_chunks(created_at DESC);

-- Indexes for embeddings
CREATE INDEX IF NOT EXISTS idx_embeddings_chunk ON embeddings(chunk_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_model ON embeddings(model_name);
CREATE INDEX IF NOT EXISTS idx_embeddings_created_at ON embeddings(created_at DESC);

-- HNSW index for vector similarity search (cosine similarity)
-- M = 16 (number of connections per node, higher = better recall but slower)
-- ef_construction = 64 (size of dynamic candidate list during construction)
CREATE INDEX IF NOT EXISTS idx_embeddings_vector_hnsw ON embeddings 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);

-- Index for combined document + version lookups
CREATE INDEX IF NOT EXISTS idx_chunks_document_version ON document_chunks(document_id, version_id);

-- Add updated_at trigger function for document_chunks
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add updated_at trigger for document_chunks
CREATE TRIGGER update_document_chunks_updated_at 
    BEFORE UPDATE ON document_chunks 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add updated_at trigger for embeddings
CREATE TRIGGER update_embeddings_updated_at 
    BEFORE UPDATE ON embeddings 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE document_chunks IS 'Stores chunked text from documents for AI processing';
COMMENT ON TABLE embeddings IS 'Stores vector embeddings for semantic search';
COMMENT ON COLUMN document_chunks.chunk_index IS 'Order of chunk within document (0-based)';
COMMENT ON COLUMN document_chunks.page_number IS 'Page number if chunk comes from a specific page';
COMMENT ON COLUMN document_chunks.section_name IS 'Section or heading name (e.g., "Introduction")';
COMMENT ON COLUMN document_chunks.token_count IS 'Approximate token count for rate limiting';
COMMENT ON COLUMN embeddings.model_name IS 'Name of the embedding model (e.g., nomic-embed-text)';
COMMENT ON COLUMN embeddings.model_version IS 'Version of the embedding model';
COMMENT ON COLUMN embeddings.dimension IS 'Dimension of the embedding vector';
COMMENT ON INDEX idx_embeddings_vector_hnsw IS 'HNSW index for fast vector similarity search using cosine distance';