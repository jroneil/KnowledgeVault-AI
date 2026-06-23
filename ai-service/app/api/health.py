from fastapi import APIRouter
from datetime import datetime
from app.core.config import settings
from app.models.schemas import HealthResponse
from app.services.ollama_client import OllamaClient

router = APIRouter()
ollama_client = OllamaClient()


@router.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Health check endpoint
    
    Returns service status and health of dependencies
    """
    # Check Ollama service
    ollama_healthy = await ollama_client.health_check()
    
    return HealthResponse(
        status="healthy",
        version=settings.APP_VERSION,
        timestamp=datetime.utcnow(),
        services={
            "ollama": ollama_healthy,
        }
    )


@router.get("/health/detailed")
async def detailed_health_check():
    """
    Detailed health check with more information
    
    Returns detailed status of all services
    """
    ollama_healthy = await ollama_client.health_check()
    
    try:
        ollama_models = await ollama_client.list_models()
        ollama_model_count = len(ollama_models)
    except Exception:
        ollama_models = []
        ollama_model_count = 0
    
    return {
        "status": "healthy" if ollama_healthy else "degraded",
        "version": settings.APP_VERSION,
        "timestamp": datetime.utcnow().isoformat(),
        "services": {
            "ollama": {
                "healthy": ollama_healthy,
                "model_count": ollama_model_count,
                "models": ollama_models[:5],  # Return first 5 models
                "base_url": settings.OLLAMA_BASE_URL
            }
        },
        "configuration": {
            "embedding_model": settings.OLLAMA_EMBEDDING_MODEL,
            "llm_model": settings.OLLAMA_LLM_MODEL,
            "chunk_size": settings.CHUNK_SIZE,
            "chunk_overlap": settings.CHUNK_OVERLAP
        }
    }