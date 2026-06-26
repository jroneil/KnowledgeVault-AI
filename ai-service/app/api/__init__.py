from fastapi import APIRouter
from .chat import router as chat_router
from .embeddings import router as embeddings_router
from .health import router as health_router
from .ingest import router as ingest_router
from .ocr import router as ocr_router
from .search import router as search_router

api_router = APIRouter()
api_router.include_router(health_router, tags=["Health"])
api_router.include_router(ingest_router, prefix="/processing", tags=["Processing"])
api_router.include_router(chat_router, prefix="/chat", tags=["Chat"])
api_router.include_router(embeddings_router, prefix="/embeddings", tags=["Embeddings"])
api_router.include_router(search_router, prefix="/search", tags=["Search"])
api_router.include_router(ocr_router, prefix="/ocr", tags=["OCR"])
