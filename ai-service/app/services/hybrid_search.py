"""
Hybrid search service combining keyword and vector similarity search.
Uses Reciprocal Rank Fusion (RRF) to merge results from both search methods.
"""

from typing import List, Dict, Tuple
from sqlalchemy import select, text, or_, and_
from sqlalchemy.sql import func

from app.services.database_service import db_service
from app.models.database import DocumentChunk, Embedding


class HybridSearchService:
    """
    Hybrid search combining keyword and vector similarity search.
    Uses Reciprocal Rank Fusion to merge and rank results.
    """
    
    # Constants for RRF algorithm
    RRF_K = 60  # RRF constant (typical range 50-100)
    
    async def hybrid_search(
        self,
        query: str,
        query_embedding: List[float],
        collection_ids: List[int] = None,
        limit: int = 10,
        vector_weight: float = 0.5,
        keyword_weight: float = 0.5,
        vector_threshold: float = 0.6,
        top_k_vector: int = 50,
        top_k_keyword: int = 50
    ) -> List[Dict]:
        """
        Perform hybrid search combining keyword and vector search.
        
        Args:
            query: Search query text
            query_embedding: Query vector embedding
            collection_ids: Filter by collection IDs
            limit: Number of results to return
            vector_weight: Weight for vector search results (0.0-1.0)
            keyword_weight: Weight for keyword search results (0.0-1.0)
            vector_threshold: Minimum similarity score for vector search
            top_k_vector: Number of vector results to retrieve for fusion
            top_k_keyword: Number of keyword results to retrieve for fusion
            
        Returns:
            List of ranked results with combined scores
        """
        # Normalize weights
        total_weight = vector_weight + keyword_weight
        if total_weight > 0:
            vector_weight = vector_weight / total_weight
            keyword_weight = keyword_weight / total_weight
        
        # Perform vector search
        vector_results = await self._vector_search(
            query_embedding=query_embedding,
            collection_ids=collection_ids,
            limit=top_k_vector,
            threshold=vector_threshold
        )
        
        # Perform keyword search
        keyword_results = await self._keyword_search(
            query=query,
            collection_ids=collection_ids,
            limit=top_k_keyword
        )
        
        # Merge results using Reciprocal Rank Fusion
        merged_results = self._reciprocal_rank_fusion(
            vector_results=vector_results,
            keyword_results=keyword_results,
            vector_weight=vector_weight,
            keyword_weight=keyword_weight
        )
        
        # Sort by combined score and return top results
        merged_results.sort(key=lambda x: x['combined_score'], reverse=True)
        return merged_results[:limit]
    
    async def _vector_search(
        self,
        query_embedding: List[float],
        collection_ids: List[int] = None,
        limit: int = 50,
        threshold: float = 0.6
    ) -> List[Tuple]:
        """Perform vector similarity search."""
        results = await db_service.vector_similarity_search(
            query_embedding=query_embedding,
            limit=limit,
            document_ids=collection_ids,
            threshold=threshold
        )
        return results
    
    async def _keyword_search(
        self,
        query: str,
        collection_ids: List[int] = None,
        limit: int = 50
    ) -> List[Tuple]:
        """
        Perform keyword-based full-text search using PostgreSQL's GIN indexes.
        """
        async with db_service.async_session() as session:
            # Use PostgreSQL full-text search
            query_text = text(f"""
                SELECT
                    chunk.*,
                    ts_rank_cd(
                        to_tsvector('english', chunk.content),
                        plainto_tsquery('english', :query),
                        32
                    ) as rank
                FROM document_chunks chunk
                WHERE to_tsvector('english', chunk.content) @@ plainto_tsquery('english', :query)
            """)
            
            # Add collection filter if provided
            if collection_ids:
                query_text = text("""
                    SELECT chunk.*, ts_rank_cd(
                        to_tsvector('english', chunk.content),
                        plainto_tsquery('english', :query),
                        32
                    ) as rank
                    FROM document_chunks chunk
                    WHERE chunk.document_id = ANY(:collection_ids)
                    AND to_tsvector('english', chunk.content) @@ plainto_tsquery('english', :query)
                """)
            
            query_text = query_text.order_by(text("rank DESC")).limit(limit)
            
            result = await session.execute(
                query_text,
                {"query": query, "collection_ids": collection_ids}
            )
            return result.fetchall()
    
    def _reciprocal_rank_fusion(
        self,
        vector_results: List[Tuple],
        keyword_results: List[Tuple],
        vector_weight: float = 0.5,
        keyword_weight: float = 0.5
    ) -> List[Dict]:
        """
        Merge results using Reciprocal Rank Fusion algorithm.
        
        RRF formula: score(d) = sum(1 / (k + rank(d)))
        Where k is a constant (typically 60) and rank is the position in the list.
        """
        scores = {}
        
        # Calculate RRF scores for vector results
        for rank, (embedding, chunk, similarity) in enumerate(vector_results, 1):
            chunk_id = chunk.id
            rrf_score = 1 / (self.RRF_K + rank)
            
            if chunk_id not in scores:
                scores[chunk_id] = {
                    'chunk': chunk,
                    'vector_rank': rank,
                    'vector_score': float(similarity),
                    'keyword_rank': None,
                    'keyword_score': None,
                    'rrf_vector': rrf_score,
                    'rrf_keyword': 0.0,
                    'combined_score': 0.0
                }
            else:
                scores[chunk_id]['vector_rank'] = rank
                scores[chunk_id]['vector_score'] = float(similarity)
                scores[chunk_id]['rrf_vector'] = rrf_score
        
        # Calculate RRF scores for keyword results
        for rank, (chunk, rank_score) in enumerate(keyword_results, 1):
            chunk_id = chunk.id
            rrf_score = 1 / (self.RRF_K + rank)
            
            if chunk_id not in scores:
                scores[chunk_id] = {
                    'chunk': chunk,
                    'vector_rank': None,
                    'vector_score': None,
                    'keyword_rank': rank,
                    'keyword_score': float(rank_score),
                    'rrf_vector': 0.0,
                    'rrf_keyword': rrf_score,
                    'combined_score': 0.0
                }
            else:
                scores[chunk_id]['keyword_rank'] = rank
                scores[chunk_id]['keyword_score'] = float(rank_score)
                scores[chunk_id]['rrf_keyword'] = rrf_score
        
        # Calculate combined scores
        for chunk_id in scores:
            rrf_combined = (
                scores[chunk_id]['rrf_vector'] * vector_weight +
                scores[chunk_id]['rrf_keyword'] * keyword_weight
            )
            scores[chunk_id]['combined_score'] = rrf_combined
        
        return list(scores.values())


# Global hybrid search service instance
hybrid_search_service = HybridSearchService()