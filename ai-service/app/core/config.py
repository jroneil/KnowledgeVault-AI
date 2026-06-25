from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application configuration"""
    
    # Database
    DATABASE_URL: str = "postgresql+asyncpg://postgres:postgres@postgres:5432/knowledgevault"
    
    # API
    API_V1_PREFIX: str = "/api/v1"
    INTERNAL_API_KEY: str = "development-only-change-me"
    CORS_ORIGINS: str = "http://localhost:3000,http://127.0.0.1:3000"

    # Ollama
    OLLAMA_BASE_URL: str = "http://host.docker.internal:11434"
    OLLAMA_EMBEDDING_MODEL: str = "nomic-embed-text"
    EMBEDDING_DIMENSION: int = 768
    OLLAMA_LLM_MODEL: str = "qwen2:7b"
    
    # Document Processing
    CHUNK_SIZE: int = 1000
    CHUNK_OVERLAP: int = 200
    MAX_FILE_SIZE: int = 50 * 1024 * 1024  # 50MB
    
    # App
    APP_NAME: str = "KnowledgeVault AI Service"
    APP_VERSION: str = "1.0.0"
    ENVIRONMENT: str = "development"
    APP_DEBUG: bool = False

    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True)

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",") if origin.strip()]

    @model_validator(mode="after")
    def validate_production_secrets(self):
        if (
            self.ENVIRONMENT.lower() == "production"
            and self.INTERNAL_API_KEY == "development-only-change-me"
        ):
            raise ValueError("INTERNAL_API_KEY must be configured in production")
        return self


settings = Settings()
