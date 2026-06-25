"""
Search API endpoints for semantic search and RAG functionality.
Provides vector similarity search and retrieval-augmented generation.
"""

from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field
from sqlalchemy import distinct, select
from sqlalchemy.sql import func

from app.core.config import settings
from app.models.database import DocumentChunk, Embedding
from app.services.database_service import db_service
from app.services.ollama_client import OllamaClient

# Security
security = HTTPBearer()

# Router
router = APIRouter()


# Request/Response Models

class SemanticSearchRequest(BaseModel):
    """Request for semantic search using vector similarity."""
    query: str = Field(..., description="Search query text", min_length=1)
    limit: int = Field(10, description="Maximum number of results", ge=1, le=100)
    threshold: float = Field(0.7, description="Similarity threshold (0.0 to 1.0)", ge=0.0, le=1.0)
    document_ids: Optional[List[int]] = Field(None, description="Filter by specific document IDs")
    model_name: Optional[str] = Field(None, description="Filter by embedding model name")


class ChunkResult(BaseModel):
    """Result from semantic search."""
    chunk_id: int
    document_id: int
    version_id: Optional[int]
    chunk_index: int
    content: str
    page_number: Optional[int]
    section_name: Optional[str]
    token_count: Optional[int]
    similarity_score: float
    created_at: str


class SemanticSearchResponse(BaseModel):
    """Response from semantic search."""
    query: str
    total_results: int
    results: List[ChunkResult]
    query_embedding_dimension: int
    search_time_ms: float


class RAGRequest(BaseModel):
    """Request for retrieval-augmented generation."""
    query: str = Field(..., description="User question or query", min_length=1)
    document_ids: Optional[List[int]] = Field(None, description="Limit search to specific documents")
    top_k: int = Field(5, description="Number of relevant chunks to retrieve", ge=1, le=20)
    similarity_threshold: float = Field(0.6, description="Minimum similarity for context", ge=0.0, le=1.0)
    max_tokens: int = Field(1000, description="Maximum tokens in LLM response", ge=100, le=4000)
    temperature: float = Field(0.7, description="LLM temperature (0.0 to 1.0)", ge=0.0, le=2.0)


class RAGContext(BaseModel):
    """Context retrieved from search."""
    chunk_id: int
    document_id: int
    chunk_index: int
    content: str
    similarity_score: float


class RAGResponse(BaseModel):
    """Response from RAG query."""
    query: str
    answer: str
    contexts: List[RAGContext]
    total_contexts: int
    model_used: str
    embedding_model: str
    processing_time_ms: float


# Helper Functions

async def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """Verify internal API token."""
    if credentials.credentials != settings.INTERNAL_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token"
        )
    return credentials.credentials


async def generate_embedding(text: str) -> List[float]:
    """Generate embedding for search query."""
    client = OllamaClient()
    try:
        embedding = await client.generate_embedding(text)
        if len(embedding) != settings.EMBEDDING_DIMENSION:
            raise ValueError(
                f"Embedding model returned {len(embedding)} dimensions; "
                f"expected {settings.EMBEDDING_DIMENSION}"
            )
        return embedding
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate embedding: {str(e)}"
        )


def build_search_context(contexts: List[tuple]) -> str:
    """Build formatted context string from retrieved chunks."""
    context_parts = []
    for i, (embedding, chunk, similarity) in enumerate(contexts, 1):
        source_info = f"[Source: Document {chunk.document_id}"
        if chunk.version_id:
            source_info += f", Version {chunk.version_id}"
        if chunk.page_number:
            source_info += f", Page {chunk.page_number}"
        if chunk.section_name:
            source_info += f", {chunk.section_name}"
        source_info += f"]"
        
        context_parts.append(f"{source_info}\n{chunk.content}")
    
    return "\n\n---\n\n".join(context_parts)


# API Endpoints

@router.post("/semantic", response_model=SemanticSearchResponse)
async def semantic_search(
    request: SemanticSearchRequest,
    token: str = Depends(verify_token)
):
    """
    Perform semantic search using vector similarity.
    
    Returns chunks ordered by similarity to the query text.
    """
    import time
    start_time = time.time()
    
    try:
        # Generate embedding for query
        query_embedding = await generate_embedding(request.query)
        
        # Perform vector similarity search
        results = await db_service.vector_similarity_search(
            query_embedding=query_embedding,
            model_name=request.model_name,
            limit=request.limit,
            document_ids=request.document_ids,
            threshold=request.threshold
        )
        
        # Format results
        formatted_results = []
        for embedding, chunk, similarity in results:
            formatted_results.append(ChunkResult(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                version_id=chunk.version_id,
                chunk_index=chunk.chunk_index,
                content=chunk.content,
                page_number=chunk.page_number,
                section_name=chunk.section_name,
                token_count=chunk.token_count,
                similarity_score=float(similarity),
                created_at=chunk.created_at.isoformat() if chunk.created_at else None
            ))
        
        search_time = (time.time() - start_time) * 1000
        
        return SemanticSearchResponse(
            query=request.query,
            total_results=len(formatted_results),
            results=formatted_results,
            query_embedding_dimension=len(query_embedding),
            search_time_ms=search_time
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Semantic search failed: {str(e)}"
        )


@router.post("/rag", response_model=RAGResponse)
async def rag_query(
    request: RAGRequest,
    token: str = Depends(verify_token)
):
    """
    Perform retrieval-augmented generation.
    
    1. Search for relevant document chunks
    2. Build context from top results
    3. Generate answer using LLM with retrieved context
    """
    import time
    start_time = time.time()
    
    try:
        # Generate embedding for query
        query_embedding = await generate_embedding(request.query)
        
        # Retrieve relevant chunks
        contexts = await db_service.vector_similarity_search(
            query_embedding=query_embedding,
            limit=request.top_k,
            document_ids=request.document_ids,
            threshold=request.similarity_threshold
        )
        
        if not contexts:
            return RAGResponse(
                query=request.query,
                answer="I couldn't find any relevant information in the documents to answer your question.",
                contexts=[],
                total_contexts=0,
                model_used=settings.OLLAMA_LLM_MODEL,
                embedding_model=settings.OLLAMA_EMBEDDING_MODEL,
                processing_time_ms=(time.time() - start_time) * 1000
            )
        
        # Build formatted context
        context_text = build_search_context(contexts)
        
        # Build prompt for LLM
        prompt = f"""You are a helpful assistant that answers questions based on the provided document excerpts.

Use the following excerpts from documents to answer the user's question. If the answer is not in the excerpts, say that you don't have enough information to answer.

Document Excerpts:
{context_text}

User Question: {request.query}

Provide a clear and accurate answer based on the excerpts above. Include specific details and cite the sources when possible."""
        
        # Generate answer using LLM
        ollama_client = OllamaClient()
        response = await ollama_client.chat_completion(
            messages=[{"role": "user", "content": prompt}],
            temperature=request.temperature,
            max_tokens=request.max_tokens
        )
        
        answer = response.get("message", {}).get("content", "")
        
        # Format contexts
        formatted_contexts = []
        for embedding, chunk, similarity in contexts:
            formatted_contexts.append(RAGContext(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                chunk_index=chunk.chunk_index,
                content=chunk.content,
                similarity_score=float(similarity)
            ))
        
        processing_time = (time.time() - start_time) * 1000
        
        return RAGResponse(
            query=request.query,
            answer=answer,
            contexts=formatted_contexts,
            total_contexts=len(formatted_contexts),
            model_used=settings.OLLAMA_LLM_MODEL,
            embedding_model=settings.OLLAMA_EMBEDDING_MODEL,
            processing_time_ms=processing_time
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"RAG query failed: {str(e)}"
        )


@router.get("/stats")
async def get_search_stats(token: str = Depends(verify_token)):
    """
    Get statistics about the search index.
    """
    try:
        async with db_service.async_session() as session:
            chunk_count_result = await session.execute(
                select(func.count()).select_from(DocumentChunk)
            )
            total_chunks = chunk_count_result.scalar()

            embedding_count_result = await session.execute(
                select(func.count()).select_from(Embedding)
            )
            total_embeddings = embedding_count_result.scalar()

            doc_count_result = await session.execute(
                select(func.count(distinct(DocumentChunk.document_id)))
            )
            total_documents = doc_count_result.scalar()
        
        return {
            "total_documents": total_documents,
            "total_chunks": total_chunks,
            "total_embeddings": total_embeddings,
            "embedding_models": [settings.OLLAMA_EMBEDDING_MODEL],
            "llm_models": [settings.OLLAMA_LLM_MODEL]
        }
    
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get search stats: {str(e)}"
        )
