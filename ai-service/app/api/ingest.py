from fastapi import APIRouter, HTTPException, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from datetime import datetime
import uuid
from app.core.config import settings
from app.models.schemas import (
    StartIngestionRequest,
    StartIngestionResponse,
    IngestionJobSchema,
    IngestionStatus,
    ExtractTextRequest,
    ExtractTextResponse,
    ChunkDocumentRequest,
    ChunkDocumentResponse
)
from app.services.extractor import DocumentExtractor
from app.services.chunker import DocumentChunker
from app.services.ollama_client import OllamaClient
from app.services.database_service import db_service

router = APIRouter()
security = HTTPBearer()
extractor = DocumentExtractor()
chunker = DocumentChunker()
ollama_client = OllamaClient()

# In-memory storage for ingestion jobs (in production, use database)
ingestion_jobs = {}


async def verify_internal_key(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """Verify internal API key for secure communication"""
    if credentials.credentials != settings.INTERNAL_API_KEY:
        raise HTTPException(status_code=403, detail="Invalid internal API key")
    return credentials


@router.post("/ingest", response_model=StartIngestionResponse)
async def start_ingestion(
    request: StartIngestionRequest,
    credentials: HTTPAuthorizationCredentials = Depends(verify_internal_key)
):
    """
    Start document ingestion process
    
    Extract text, chunk it, and generate embeddings
    """
    job_id = str(uuid.uuid4())
    
    # Create ingestion job
    ingestion_jobs[job_id] = IngestionJobSchema(
        job_id=job_id,
        document_id=request.document_id,
        version_id=request.version_id,
        status=IngestionStatus.PENDING,
        progress=0,
        message="Ingestion job created",
        chunks_processed=0,
        embeddings_generated=0,
        created_at=datetime.utcnow(),
        updated_at=datetime.utcnow()
    )
    
    # Start processing asynchronously
    # In production, use a proper task queue like Celery or Redis
    # For now, we'll process synchronously for Phase 3
    try:
        await process_document(job_id, request)
    except Exception as e:
        ingestion_jobs[job_id].status = IngestionStatus.FAILED
        ingestion_jobs[job_id].error = str(e)
        ingestion_jobs[job_id].message = f"Failed to process document: {e}"
        ingestion_jobs[job_id].updated_at = datetime.utcnow()
    
    return StartIngestionResponse(
        job_id=job_id,
        document_id=request.document_id,
        version_id=request.version_id,
        status=ingestion_jobs[job_id].status,
        message="Ingestion job started"
    )


@router.get("/ingest/{job_id}", response_model=IngestionJobSchema)
async def get_ingestion_status(
    job_id: str,
    credentials: HTTPAuthorizationCredentials = Depends(verify_internal_key)
):
    """
    Get status of an ingestion job
    """
    if job_id not in ingestion_jobs:
        raise HTTPException(status_code=404, detail="Ingestion job not found")
    
    return ingestion_jobs[job_id]


@router.post("/extract", response_model=ExtractTextResponse)
async def extract_text(
    request: ExtractTextRequest,
    credentials: HTTPAuthorizationCredentials = Depends(verify_internal_key)
):
    """
    Extract text from a document
    
    This endpoint extracts text from various document formats
    """
    try:
        text = await extractor.extract_text(request.file_path, request.mime_type)
        
        return ExtractTextResponse(
            text=text,
            metadata={
                "char_count": len(text),
                "word_count": len(text.split()),
                "extracted_at": datetime.utcnow().isoformat()
            },
            success=True
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Text extraction failed: {str(e)}")


@router.post("/chunk", response_model=ChunkDocumentResponse)
async def chunk_document(
    request: ChunkDocumentRequest,
    credentials: HTTPAuthorizationCredentials = Depends(verify_internal_key)
):
    """
    Chunk document text into smaller pieces
    
    This endpoint splits text into chunks with configurable size and overlap
    """
    try:
        chunks = await chunker.chunk_document(
            text=request.text,
            chunk_size=request.chunk_size,
            overlap=request.overlap
        )
        
        total_tokens = sum(chunk.token_count for chunk in chunks)
        
        return ChunkDocumentResponse(
            chunks=chunks,
            total_chunks=len(chunks),
            total_tokens=total_tokens,
            success=True
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Document chunking failed: {str(e)}")


async def process_document(job_id: str, request: StartIngestionRequest):
    """
    Process document: extract text, chunk, generate embeddings, and store in database
    
    This is the main ingestion workflow for Phase 4
    """
    job = ingestion_jobs[job_id]
    
    # Step 1: Update status to processing
    job.status = IngestionStatus.PROCESSING
    job.message = "Extracting text from document"
    job.progress = 10
    job.updated_at = datetime.utcnow()
    
    try:
        # Step 2: Extract text
        text = await extractor.extract_text(request.file_path, request.mime_type)
        job.progress = 30
        job.message = "Text extraction completed"
        
        # Step 3: Chunk the document
        job.message = "Chunking document"
        job.progress = 40
        chunks = await chunker.chunk_document(text)
        job.chunks_processed = len(chunks)
        job.progress = 60
        job.message = f"Document chunked into {len(chunks)} chunks"
        
        # Step 4: Save chunks to database
        job.message = "Saving chunks to database"
        job.progress = 65
        
        chunks_data = []
        for i, chunk in enumerate(chunks):
            chunk_data = {
                'document_id': request.document_id,
                'version_id': request.version_id,
                'chunk_index': i,
                'content': chunk.content,
                'page_number': chunk.page_number,
                'section_name': chunk.section,
                'token_count': chunk.token_count
            }
            chunks_data.append(chunk_data)
        
        saved_chunks = await db_service.save_chunks_batch(chunks_data)
        job.message = f"Saved {len(saved_chunks)} chunks to database"
        job.progress = 70
        
        # Step 5: Generate embeddings and save to database
        job.message = "Generating embeddings"
        job.progress = 75
        
        embeddings_data = []
        for i, (saved_chunk, chunk_schema) in enumerate(zip(saved_chunks, chunks)):
            try:
                embedding_vector = await ollama_client.generate_embedding(chunk_schema.content)
                if len(embedding_vector) != settings.EMBEDDING_DIMENSION:
                    raise ValueError(
                        f"Embedding model returned {len(embedding_vector)} dimensions; "
                        f"expected {settings.EMBEDDING_DIMENSION}"
                    )
                
                embedding_data = {
                    'chunk_id': saved_chunk.id,
                    'model_name': settings.OLLAMA_EMBEDDING_MODEL,
                    'model_version': 'latest',
                    'embedding': embedding_vector,
                    'dimension': len(embedding_vector)
                }
                embeddings_data.append(embedding_data)
                
                job.embeddings_generated = i + 1
                job.progress = 75 + int((i + 1) / len(chunks) * 20)
                
            except Exception as e:
                print(f"Failed to generate embedding for chunk {i}: {e}")
                continue
        
        # Save embeddings in batch
        if embeddings_data:
            saved_embeddings = await db_service.save_embeddings_batch(embeddings_data)
            job.message = f"Saved {len(saved_embeddings)} embeddings to database"
        
        # Step 6: Mark as completed
        job.status = IngestionStatus.COMPLETED
        job.message = f"Document ingestion completed successfully: {len(saved_chunks)} chunks, {len(saved_embeddings)} embeddings"
        job.progress = 100
        job.updated_at = datetime.utcnow()
        
    except Exception as e:
        job.status = IngestionStatus.FAILED
        job.error = str(e)
        job.message = f"Processing failed: {str(e)}"
        job.updated_at = datetime.utcnow()
        # Clean up partial data on failure
        try:
            await db_service.delete_chunks_by_document(request.document_id)
        except Exception as cleanup_error:
            print(f"Failed to clean up failed ingestion: {cleanup_error}")
