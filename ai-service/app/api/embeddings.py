from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from app.core.config import settings
from app.models.schemas import (
    GenerateEmbeddingRequest,
    GenerateEmbeddingResponse,
    GenerateEmbeddingsBatchRequest,
    GenerateEmbeddingsBatchResponse,
)
from app.services.ollama_client import OllamaClient

router = APIRouter()
security = HTTPBearer()
ollama_client = OllamaClient()


async def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    if credentials.credentials != settings.INTERNAL_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token"
        )
    return credentials.credentials


@router.post("", response_model=GenerateEmbeddingResponse)
async def generate_embedding(
    request: GenerateEmbeddingRequest,
    token: str = Depends(verify_token)
):
    try:
        embedding = await ollama_client.generate_embedding(request.text, request.model)
        return GenerateEmbeddingResponse(
            embedding=embedding,
            model=request.model or settings.OLLAMA_EMBEDDING_MODEL,
            success=True
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate embedding: {str(e)}"
        )


@router.post("/batch", response_model=GenerateEmbeddingsBatchResponse)
async def generate_embeddings_batch(
    request: GenerateEmbeddingsBatchRequest,
    token: str = Depends(verify_token)
):
    try:
        embeddings = await ollama_client.generate_embeddings_batch(request.texts, request.model)
        return GenerateEmbeddingsBatchResponse(
            embeddings=embeddings,
            model=request.model or settings.OLLAMA_EMBEDDING_MODEL,
            total_embeddings=len(embeddings),
            success=True
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate embeddings: {str(e)}"
        )
