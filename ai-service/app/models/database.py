"""
SQLAlchemy database models for document chunks and embeddings.
Supports vector storage and similarity search with pgvector.
"""

from datetime import datetime
from sqlalchemy import (
    Column, BigInteger, Integer, String, Text, TIMESTAMP, 
    ForeignKey, CheckConstraint, UniqueConstraint, Index
)
from sqlalchemy.dialects.postgresql import ARRAY
from pgvector.sqlalchemy import Vector
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class DocumentChunk(Base):
    """
    Represents a chunk of text extracted from a document.
    Chunks are created by splitting documents into smaller pieces for AI processing.
    """
    __tablename__ = 'document_chunks'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    document_id = Column(BigInteger, ForeignKey('documents.id', ondelete='CASCADE'), nullable=False)
    version_id = Column(BigInteger, ForeignKey('document_versions.id', ondelete='CASCADE'))
    chunk_index = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    page_number = Column(Integer, nullable=True)
    section_name = Column(String(255), nullable=True)
    token_count = Column(Integer, nullable=True)
    created_at = Column(TIMESTAMP, default=datetime.utcnow, nullable=False)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
    
    # Constraints
    __table_args__ = (
        CheckConstraint('chunk_index >= 0', name='chk_chunk_index'),
        CheckConstraint('token_count >= 0', name='chk_token_count'),
        UniqueConstraint('document_id', 'version_id', 'chunk_index', name='uq_document_version_chunk'),
        Index('idx_chunks_document', 'document_id'),
        Index('idx_chunks_version', 'version_id'),
        Index('idx_chunks_created_at', 'created_at', postgresql_ops={'created_at': 'DESC'}),
        Index('idx_chunks_document_version', 'document_id', 'version_id'),
    )
    
    def __repr__(self):
        return f"<DocumentChunk(id={self.id}, document_id={self.document_id}, chunk_index={self.chunk_index})>"
    
    def to_dict(self):
        """Convert to dictionary for API responses."""
        return {
            'id': self.id,
            'document_id': self.document_id,
            'version_id': self.version_id,
            'chunk_index': self.chunk_index,
            'content': self.content,
            'page_number': self.page_number,
            'section_name': self.section_name,
            'token_count': self.token_count,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }


class Embedding(Base):
    """
    Represents a vector embedding for a document chunk.
    Enables semantic search and similarity matching.
    """
    __tablename__ = 'embeddings'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    chunk_id = Column(BigInteger, ForeignKey('document_chunks.id', ondelete='CASCADE'), nullable=False)
    model_name = Column(String(100), nullable=False)
    model_version = Column(String(50), nullable=True)
    embedding = Column(Vector(768), nullable=False)
    dimension = Column(Integer, nullable=False)
    created_at = Column(TIMESTAMP, default=datetime.utcnow, nullable=False)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
    
    # Constraints
    __table_args__ = (
        CheckConstraint('dimension > 0', name='chk_dimension'),
        UniqueConstraint('chunk_id', 'model_name', name='uq_chunk_model'),
        Index('idx_embeddings_chunk', 'chunk_id'),
        Index('idx_embeddings_model', 'model_name'),
        Index('idx_embeddings_created_at', 'created_at', postgresql_ops={'created_at': 'DESC'}),
    )
    
    def __repr__(self):
        return f"<Embedding(id={self.id}, chunk_id={self.chunk_id}, model={self.model_name})>"
    
    def to_dict(self):
        """Convert to dictionary for API responses."""
        return {
            'id': self.id,
            'chunk_id': self.chunk_id,
            'model_name': self.model_name,
            'model_version': self.model_version,
            'dimension': self.dimension,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
            # Note: embedding vector is typically excluded from API responses due to size
        }
