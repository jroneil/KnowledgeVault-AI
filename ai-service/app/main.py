from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from app.core.config import settings
from app.api import api_router
from app.api.health import router as health_router
from app.api.ocr import router as ocr_router
from app.api.search import router as search_router
from app.services.ollama_client import OllamaClient
from app.services.ocr_service import ocr_service


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan events for startup and shutdown"""
    # Startup
    print(f"Starting {settings.APP_NAME} v{settings.APP_VERSION}")
    print(f"OLLAMA_BASE_URL: {settings.OLLAMA_BASE_URL}")
    
    # Check Ollama service
    ollama_client = OllamaClient()
    ollama_healthy = await ollama_client.health_check()
    if ollama_healthy:
        print("✓ Ollama service is available")
        try:
            models = await ollama_client.list_models()
            print(f"✓ Available Ollama models: {', '.join(models[:3])}...")
        except Exception as e:
            print(f"✓ Ollama connected, but couldn't list models: {e}")
    else:
        print("⚠ Ollama service is not available yet")
    
    yield
    
    # Shutdown
    print(f"Shutting down {settings.APP_NAME}")


# Create FastAPI app
app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="AI Service for KnowledgeVault - Document processing, embeddings, and RAG capabilities",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify exact origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routes
app.include_router(api_router, prefix=settings.API_V1_PREFIX)

# Include search router
app.include_router(search_router, tags=["search"])

# Include OCR router
app.include_router(ocr_router, tags=["ocr"])

# Include health router for root-level health endpoints
app.include_router(health_router)


# Root endpoint
@app.get("/")
async def root():
    """Root endpoint with service information"""
    return {
        "service": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "status": "running",
        "docs": "/docs",
        "health": "/health",
        "api_prefix": settings.API_V1_PREFIX
    }


# Health check at root level
@app.get("/health")
async def health():
    """Simple health check endpoint"""
    return {"status": "healthy"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG,
        log_level="info"
    )