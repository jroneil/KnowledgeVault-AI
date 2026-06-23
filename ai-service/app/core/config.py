from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """Application configuration"""
    
    # Database
    DATABASE_URL: str = "postgresql+asyncpg://postgres:postgres@postgres:5432/knowledgevault"
    
    # API
    API_V1_PREFIX: str = "/api/v1"
    INTERNAL_API_KEY: str = "9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe"
    
    # Ollama
    OLLAMA_BASE_URL: str = "http://192.168.1.24:11434"
    OLLAMA_EMBEDDING_MODEL: str = "nomic-embed-text"
    OLLAMA_LLM_MODEL: str = "qwen2:7b"
    
    # Document Processing
    CHUNK_SIZE: int = 1000
    CHUNK_OVERLAP: int = 200
    MAX_FILE_SIZE: int = 50 * 1024 * 1024  # 50MB
    
    # App
    APP_NAME: str = "KnowledgeVault AI Service"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()