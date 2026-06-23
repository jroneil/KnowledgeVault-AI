"""
Multi-model service for supporting multiple embedding and LLM models.
Allows dynamic model selection and comparison.
"""

from typing import List, Dict, Optional
from enum import Enum


class ModelType(Enum):
    """Types of AI models."""
    EMBEDDING = "embedding"
    LLM = "llm"
    RERANKER = "reranker"


class ModelCapability(Enum):
    """Capabilities of AI models."""
    TEXT_EMBEDDING = "text_embedding"
    CHAT_COMPLETION = "chat_completion"
    RERANKING = "reranking"
    CODE_GENERATION = "code_generation"
    MULTILINGUAL = "multilingual"


class ModelInfo:
    """Information about an AI model."""
    
    def __init__(
        self,
        name: str,
        model_type: ModelType,
        dimension: Optional[int] = None,
        capabilities: List[ModelCapability] = None,
        description: str = "",
        max_tokens: Optional[int] = None,
        context_length: Optional[int] = None
    ):
        self.name = name
        self.model_type = model_type
        self.dimension = dimension
        self.capabilities = capabilities or []
        self.description = description
        self.max_tokens = max_tokens
        self.context_length = context_length
    
    def to_dict(self) -> Dict:
        """Convert to dictionary."""
        return {
            'name': self.name,
            'type': self.model_type.value,
            'dimension': self.dimension,
            'capabilities': [cap.value for cap in self.capabilities],
            'description': self.description,
            'max_tokens': self.max_tokens,
            'context_length': self.context_length
        }


class MultiModelService:
    """
    Service for managing and using multiple AI models.
    """
    
    # Available models registry
    AVAILABLE_MODELS = {
        # Embedding models
        'nomic-embed-text': ModelInfo(
            name='nomic-embed-text',
            model_type=ModelType.EMBEDDING,
            dimension=768,
            capabilities=[ModelCapability.TEXT_EMBEDDING],
            description='Fast and efficient text embedding model',
            context_length=8192
        ),
        'bge-m3': ModelInfo(
            name='bge-m3',
            model_type=ModelType.EMBEDDING,
            dimension=1024,
            capabilities=[
                ModelCapability.TEXT_EMBEDDING,
                ModelCapability.MULTILINGUAL
            ],
            description='Multilingual embedding model with high accuracy',
            context_length=8192
        ),
        'mxbai-embed-large': ModelInfo(
            name='mxbai-embed-large',
            model_type=ModelType.EMBEDDING,
            dimension=1024,
            capabilities=[ModelCapability.TEXT_EMBEDDING],
            description='Large embedding model for complex queries',
            context_length=512
        ),
        'all-minilm': ModelInfo(
            name='all-minilm',
            model_type=ModelType.EMBEDDING,
            dimension=384,
            capabilities=[ModelCapability.TEXT_EMBEDDING],
            description='Lightweight and fast embedding model',
            context_length=512
        ),
        
        # LLM models
        'qwen2:7b': ModelInfo(
            name='qwen2:7b',
            model_type=ModelType.LLM,
            capabilities=[
                ModelCapability.CHAT_COMPLETION,
                ModelCapability.CODE_GENERATION
            ],
            description='General-purpose LLM for chat and generation',
            max_tokens=4096,
            context_length=8192
        ),
        'llama3:8b': ModelInfo(
            name='llama3:8b',
            model_type=ModelType.LLM,
            capabilities=[
                ModelCapability.CHAT_COMPLETION,
                ModelCapability.CODE_GENERATION
            ],
            description='State-of-the-art LLM for various tasks',
            max_tokens=4096,
            context_length=8192
        ),
        'gemma2:9b': ModelInfo(
            name='gemma2:9b',
            model_type=ModelType.LLM,
            capabilities=[
                ModelCapability.CHAT_COMPLETION,
                ModelCapability.MULTILINGUAL
            ],
            description='Multilingual LLM with strong reasoning',
            max_tokens=8192,
            context_length=8192
        ),
        'deepseek-coder:6.7b': ModelInfo(
            name='deepseek-coder:6.7b',
            model_type=ModelType.LLM,
            capabilities=[
                ModelCapability.CHAT_COMPLETION,
                ModelCapability.CODE_GENERATION
            ],
            description='Specialized LLM for code generation',
            max_tokens=4096,
            context_length=16384
        ),
    }
    
    # Default models
    DEFAULT_EMBEDDING_MODEL = 'nomic-embed-text'
    DEFAULT_LLM_MODEL = 'qwen2:7b'
    
    def __init__(self):
        """Initialize multi-model service."""
        from app.services.ollama_client import ollama_client
        self.ollama_client = ollama_client
    
    def get_available_models(
        self,
        model_type: Optional[ModelType] = None
    ) -> List[Dict]:
        """
        Get list of available models.
        
        Args:
            model_type: Filter by model type (optional)
            
        Returns:
            List of model information dictionaries
        """
        models = []
        
        for model_name, model_info in self.AVAILABLE_MODELS.items():
            if model_type is None or model_info.model_type == model_type:
                models.append(model_info.to_dict())
        
        return models
    
    def get_model_info(self, model_name: str) -> Optional[Dict]:
        """
        Get information about a specific model.
        
        Args:
            model_name: Name of the model
            
        Returns:
            Model information or None if not found
        """
        model_info = self.AVAILABLE_MODELS.get(model_name)
        return model_info.to_dict() if model_info else None
    
    def is_model_available(self, model_name: str) -> bool:
        """
        Check if a model is available in the registry.
        
        Args:
            model_name: Name of the model
            
        Returns:
            True if model is available
        """
        return model_name in self.AVAILABLE_MODELS
    
    async def generate_embedding(
        self,
        text: str,
        model_name: Optional[str] = None
    ) -> List[float]:
        """
        Generate text embedding using specified model.
        
        Args:
            text: Input text
            model_name: Model to use (uses default if not specified)
            
        Returns:
            Embedding vector
        """
        model_name = model_name or self.DEFAULT_EMBEDDING_MODEL
        
        if not self.is_model_available(model_name):
            raise ValueError(f"Model {model_name} is not available")
        
        model_info = self.AVAILABLE_MODELS[model_name]
        
        if model_info.model_type != ModelType.EMBEDDING:
            raise ValueError(f"Model {model_name} is not an embedding model")
        
        # Generate embedding using Ollama client
        return await self.ollama_client.generate_embedding(text, model_name)
    
    async def chat_completion(
        self,
        messages: List[Dict],
        model_name: Optional[str] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        """
        Generate chat completion using specified model.
        
        Args:
            messages: Chat messages
            model_name: Model to use (uses default if not specified)
            max_tokens: Maximum tokens to generate
            
        Returns:
            Generated response
        """
        model_name = model_name or self.DEFAULT_LLM_MODEL
        
        if not self.is_model_available(model_name):
            raise ValueError(f"Model {model_name} is not available")
        
        model_info = self.AVAILABLE_MODELS[model_name]
        
        if model_info.model_type != ModelType.LLM:
            raise ValueError(f"Model {model_name} is not an LLM")
        
        # Generate completion using Ollama client
        return await self.ollama_client.chat_completion(messages, model_name, max_tokens)
    
    def recommend_embedding_model(
        self,
        text_length: int,
        multilingual: bool = False,
        priority: str = "speed"  # "speed", "quality", "balanced"
    ) -> str:
        """
        Recommend an embedding model based on use case.
        
        Args:
            text_length: Length of input text
            multilingual: Whether multilingual support is needed
            priority: Optimization priority
            
        Returns:
            Recommended model name
        """
        if multilingual:
            return 'bge-m3'
        
        if priority == "speed":
            return 'all-minilm'
        elif priority == "quality":
            return 'bge-m3'
        else:  # balanced
            return self.DEFAULT_EMBEDDING_MODEL
    
    def recommend_llm_model(
        self,
        task: str = "chat",
        code_generation: bool = False,
        multilingual: bool = False,
        priority: str = "balanced"
    ) -> str:
        """
        Recommend an LLM model based on use case.
        
        Args:
            task: Type of task
            code_generation: Whether code generation is needed
            multilingual: Whether multilingual support is needed
            priority: Optimization priority
            
        Returns:
            Recommended model name
        """
        if code_generation:
            return 'deepseek-coder:6.7b'
        
        if multilingual:
            return 'gemma2:9b'
        
        if priority == "speed":
            return self.DEFAULT_LLM_MODEL  # qwen2:7b is relatively fast
        elif priority == "quality":
            return 'llama3:8b'
        else:  # balanced
            return self.DEFAULT_LLM_MODEL
    
    async def compare_embeddings(
        self,
        text: str,
        models: Optional[List[str]] = None
    ) -> Dict[str, List[float]]:
        """
        Generate embeddings using multiple models for comparison.
        
        Args:
            text: Input text
            models: List of models to use (all embedding models if not specified)
            
        Returns:
            Dictionary mapping model names to embeddings
        """
        if models is None:
            models = [
                name for name, info in self.AVAILABLE_MODELS.items()
                if info.model_type == ModelType.EMBEDDING
            ]
        
        embeddings = {}
        
        for model_name in models:
            try:
                embedding = await self.generate_embedding(text, model_name)
                embeddings[model_name] = embedding
            except Exception as e:
                print(f"Error generating embedding with {model_name}: {e}")
        
        return embeddings


# Global multi-model service instance
multi_model_service = MultiModelService()