"""
Database service for managing document chunks and embeddings.
Provides async CRUD operations with PostgreSQL and pgvector.
"""

import asyncio
from typing import List, Optional
from datetime import datetime
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import delete, select, text
from sqlalchemy.sql import func

from app.core.config import settings
from app.models.database import DocumentChunk, Embedding, Base


class DatabaseService:
    """
    Async database service for chunks and embeddings.
    Manages PostgreSQL connections and provides CRUD operations.
    """
    
    def __init__(self):
        self.engine = create_async_engine(
            settings.DATABASE_URL,
            echo=settings.APP_DEBUG,
            pool_pre_ping=True,
            pool_size=10,
            max_overflow=20
        )
        self.async_session = sessionmaker(
            self.engine,
            class_=AsyncSession,
            expire_on_commit=False
        )
    
    async def get_session(self) -> AsyncSession:
        """Get a new database session."""
        async with self.async_session() as session:
            yield session
    
    async def health_check(self) -> bool:
        """Check database connection health."""
        try:
            async with self.async_session() as session:
                await session.execute(text("SELECT 1"))
                return True
        except Exception:
            return False
    
    async def close(self):
        """Close database connections."""
        await self.engine.dispose()
    
    # Chunk Operations
    
    async def save_chunk(
        self,
        document_id: int,
        version_id: int,
        chunk_index: int,
        content: str,
        page_number: Optional[int] = None,
        section_name: Optional[str] = None,
        token_count: Optional[int] = None
    ) -> DocumentChunk:
        """Save a document chunk to the database."""
        async with self.async_session() as session:
            chunk = DocumentChunk(
                document_id=document_id,
                version_id=version_id,
                chunk_index=chunk_index,
                content=content,
                page_number=page_number,
                section_name=section_name,
                token_count=token_count
            )
            session.add(chunk)
            await session.commit()
            await session.refresh(chunk)
            return chunk
    
    async def save_chunks_batch(
        self,
        chunks_data: List[dict]
    ) -> List[DocumentChunk]:
        """Save multiple chunks in a batch."""
        async with self.async_session() as session:
            chunks = [
                DocumentChunk(**data)
                for data in chunks_data
            ]
            session.add_all(chunks)
            await session.commit()
            
            # Refresh all chunks to get their IDs
            for chunk in chunks:
                await session.refresh(chunk)
            
            return chunks
    
    async def get_chunks_by_document(
        self,
        document_id: int,
        version_id: Optional[int] = None
    ) -> List[DocumentChunk]:
        """Get all chunks for a document."""
        async with self.async_session() as session:
            query = select(DocumentChunk).where(
                DocumentChunk.document_id == document_id
            )
            
            if version_id is not None:
                query = query.where(DocumentChunk.version_id == version_id)
            
            query = query.order_by(DocumentChunk.chunk_index)
            result = await session.execute(query)
            return result.scalars().all()
    
    async def get_chunk_by_id(self, chunk_id: int) -> Optional[DocumentChunk]:
        """Get a specific chunk by ID."""
        async with self.async_session() as session:
            result = await session.execute(
                select(DocumentChunk).where(DocumentChunk.id == chunk_id)
            )
            return result.scalar_one_or_none()
    
    async def delete_chunks_by_document(self, document_id: int) -> int:
        """Delete all chunks for a document."""
        async with self.async_session() as session:
            result = await session.execute(
                delete(DocumentChunk).where(
                    DocumentChunk.document_id == document_id
                )
            )
            await session.commit()
            return result.rowcount
    
    # Embedding Operations
    
    async def save_embedding(
        self,
        chunk_id: int,
        model_name: str,
        embedding: List[float],
        model_version: Optional[str] = None
    ) -> Embedding:
        """Save an embedding for a chunk."""
        async with self.async_session() as session:
            embedding_record = Embedding(
                chunk_id=chunk_id,
                model_name=model_name,
                model_version=model_version,
                embedding=embedding,
                dimension=len(embedding)
            )
            session.add(embedding_record)
            await session.commit()
            await session.refresh(embedding_record)
            return embedding_record
    
    async def save_embeddings_batch(
        self,
        embeddings_data: List[dict]
    ) -> List[Embedding]:
        """Save multiple embeddings in a batch."""
        async with self.async_session() as session:
            embeddings = [
                Embedding(**data)
                for data in embeddings_data
            ]
            session.add_all(embeddings)
            await session.commit()
            
            for emb in embeddings:
                await session.refresh(emb)
            
            return embeddings
    
    async def get_embedding_by_chunk(
        self,
        chunk_id: int,
        model_name: Optional[str] = None
    ) -> Optional[Embedding]:
        """Get embedding for a chunk."""
        async with self.async_session() as session:
            query = select(Embedding).where(Embedding.chunk_id == chunk_id)
            
            if model_name:
                query = query.where(Embedding.model_name == model_name)
            
            result = await session.execute(query)
            return result.scalar_one_or_none()
    
    async def get_embeddings_by_document(
        self,
        document_id: int,
        version_id: Optional[int] = None
    ) -> List[Embedding]:
        """Get all embeddings for a document."""
        async with self.async_session() as session:
            # Join with chunks to filter by document
            query = select(Embedding).join(
                DocumentChunk, Embedding.chunk_id == DocumentChunk.id
            ).where(
                DocumentChunk.document_id == document_id
            )
            
            if version_id:
                query = query.where(DocumentChunk.version_id == version_id)
            
            result = await session.execute(query)
            return result.scalars().all()
    
    async def delete_embeddings_by_chunk(self, chunk_id: int) -> int:
        """Delete embeddings for a specific chunk."""
        async with self.async_session() as session:
            result = await session.execute(
                delete(Embedding).where(Embedding.chunk_id == chunk_id)
            )
            await session.commit()
            return result.rowcount
    
    async def delete_embeddings_by_document(self, document_id: int) -> int:
        """Delete all embeddings for a document."""
        async with self.async_session() as session:
            # Delete through chunk relationship
            subquery = select(DocumentChunk.id).where(
                DocumentChunk.document_id == document_id
            )
            
            result = await session.execute(
                delete(Embedding).where(Embedding.chunk_id.in_(subquery))
            )
            await session.commit()
            return result.rowcount
    
    # Vector Similarity Search
    
    async def vector_similarity_search(
        self,
        query_embedding: List[float],
        model_name: Optional[str] = None,
        limit: int = 10,
        document_ids: Optional[List[int]] = None,
        threshold: float = 0.7
    ) -> List[tuple]:
        """
        Perform vector similarity search using cosine distance.
        Returns list of (embedding, chunk, distance_score) tuples.
        """
        async with self.async_session() as session:
            # Convert query embedding to pgvector array for comparison
            from pgvector.sqlalchemy import Vector
            
            # Build query with vector similarity
            query = select(
                Embedding,
                DocumentChunk,
                (1 - Embedding.embedding.cosine_distance(query_embedding)).label('similarity')
            ).join(
                DocumentChunk, Embedding.chunk_id == DocumentChunk.id
            )
            
            # Filter by similarity threshold
            query = query.where(
                (1 - Embedding.embedding.cosine_distance(query_embedding)) >= threshold
            )
            
            if model_name:
                query = query.where(Embedding.model_name == model_name)
            
            if document_ids:
                query = query.where(DocumentChunk.document_id.in_(document_ids))
            
            # Order by similarity (highest first)
            query = query.order_by(
                (1 - Embedding.embedding.cosine_distance(query_embedding)).desc()
            ).limit(limit)
            
            result = await session.execute(query)
            return result.all()
    
    async def get_document_chunk_count(self, document_id: int) -> int:
        """Get the number of chunks for a document."""
        async with self.async_session() as session:
            result = await session.execute(
                select(func.count()).select_from(DocumentChunk).where(
                    DocumentChunk.document_id == document_id
                )
            )
            return result.scalar()


# Global database service instance
db_service = DatabaseService()
