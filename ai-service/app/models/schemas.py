from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from datetime import datetime
from enum import Enum


class IngestionStatus(str, Enum):
    """Status of document ingestion"""
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class DocumentChunkSchema(BaseModel):
    """Schema for document chunk"""
    chunk_index: int
    content: str
    page_number: Optional[int] = None
    section_name: Optional[str] = None
    token_count: int = 0


class ExtractTextRequest(BaseModel):
    """Request schema for text extraction"""
    file_path: str
    mime_type: str = Field(..., description="MIME type of the document")


class ExtractTextResponse(BaseModel):
    """Response schema for text extraction"""
    text: str
    metadata: Dict[str, Any] = {}
    success: bool


class ChunkDocumentRequest(BaseModel):
    """Request schema for document chunking"""
    text: str
    chunk_size: Optional[int] = Field(default=1000, ge=100, le=10000)
    overlap: Optional[int] = Field(default=200, ge=0, le=1000)


class ChunkDocumentResponse(BaseModel):
    """Response schema for document chunking"""
    chunks: List[DocumentChunkSchema]
    total_chunks: int
    total_tokens: int
    success: bool


class GenerateEmbeddingRequest(BaseModel):
    """Request schema for embedding generation"""
    text: str
    model: Optional[str] = Field(default="nomic-embed-text")


class GenerateEmbeddingResponse(BaseModel):
    """Response schema for embedding generation"""
    embedding: List[float]
    model: str
    success: bool


class GenerateEmbeddingsBatchRequest(BaseModel):
    """Request schema for batch embedding generation"""
    texts: List[str] = Field(..., min_items=1, max_items=100)
    model: Optional[str] = Field(default="nomic-embed-text")


class GenerateEmbeddingsBatchResponse(BaseModel):
    """Response schema for batch embedding generation"""
    embeddings: List[List[float]]
    model: str
    total_embeddings: int
    success: bool


class ChatCompletionRequest(BaseModel):
    """Request schema for chat completion"""
    messages: List[Dict[str, str]] = Field(..., min_items=1)
    model: Optional[str] = Field(default="qwen2:7b")
    temperature: Optional[float] = Field(default=0.7, ge=0.0, le=2.0)
    max_tokens: Optional[int] = Field(default=2000, ge=1, le=8000)


class ChatCompletionResponse(BaseModel):
    """Response schema for chat completion"""
    response: str
    model: str
    success: bool


class StartIngestionRequest(BaseModel):
    """Request schema for starting document ingestion"""
    document_id: int
    version_id: int
    file_path: str
    mime_type: str


class IngestionJobSchema(BaseModel):
    """Schema for ingestion job"""
    job_id: str
    document_id: int
    version_id: int
    status: IngestionStatus
    progress: int = 0
    message: Optional[str] = None
    chunks_processed: int = 0
    embeddings_generated: int = 0
    created_at: datetime
    updated_at: datetime
    error: Optional[str] = None


class StartIngestionResponse(BaseModel):
    """Response schema for starting ingestion"""
    job_id: str
    document_id: int
    version_id: int
    status: IngestionStatus
    message: str


class HealthResponse(BaseModel):
    """Response schema for health check"""
    status: str
    version: str
    timestamp: datetime
    services: Dict[str, bool]


class ErrorResponse(BaseModel):
    """Response schema for errors"""
    error: str
    detail: Optional[str] = None
    timestamp: datetime