import httpx
from typing import List, Optional
from app.core.config import settings


class OllamaClient:
    """Client for interacting with Ollama API"""
    
    def __init__(self, base_url: str = None):
        self.base_url = base_url or settings.OLLAMA_BASE_URL
        self.timeout = 120.0  # 2 minutes timeout for LLM requests
    
    async def generate_embedding(
        self,
        text: str,
        model: Optional[str] = None
    ) -> List[float]:
        """
        Generate embedding for text using Ollama
        
        Args:
            text: Text to generate embedding for
            model: Model name (default from settings)
            
        Returns:
            List of embedding values
        """
        model = model or settings.OLLAMA_EMBEDDING_MODEL
        
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.post(
                    f"{self.base_url}/api/embeddings",
                    json={
                        "model": model,
                        "prompt": text
                    }
                )
                response.raise_for_status()
                
                data = response.json()
                return data.get("embedding", [])
                
            except httpx.HTTPError as e:
                raise ValueError(f"Failed to generate embedding: {e}")
            except Exception as e:
                raise ValueError(f"Unexpected error generating embedding: {e}")
    
    async def generate_embeddings_batch(
        self,
        texts: List[str],
        model: Optional[str] = None
    ) -> List[List[float]]:
        """
        Generate embeddings for multiple texts
        
        Args:
            texts: List of texts to generate embeddings for
            model: Model name (default from settings)
            
        Returns:
            List of embedding vectors
        """
        embeddings = []
        
        for text in texts:
            embedding = await self.generate_embedding(text, model)
            embeddings.append(embedding)
        
        return embeddings
    
    async def chat_completion(
        self,
        messages: List[dict],
        model: Optional[str] = None,
        stream: bool = False,
        temperature: float = 0.7,
        max_tokens: int = 2000
    ) -> str:
        """
        Generate chat completion using Ollama
        
        Args:
            messages: List of message dictionaries with 'role' and 'content'
            model: Model name (default from settings)
            stream: Whether to stream response
            temperature: Sampling temperature
            max_tokens: Maximum tokens to generate
            
        Returns:
            Generated response text
        """
        model = model or settings.OLLAMA_LLM_MODEL
        
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.post(
                    f"{self.base_url}/api/chat",
                    json={
                        "model": model,
                        "messages": messages,
                        "stream": stream,
                        "options": {
                            "temperature": temperature,
                            "num_predict": max_tokens
                        }
                    }
                )
                response.raise_for_status()
                
                data = response.json()
                return data.get("message", {}).get("content", "")
                
            except httpx.HTTPError as e:
                raise ValueError(f"Failed to generate chat completion: {e}")
            except Exception as e:
                raise ValueError(f"Unexpected error generating chat completion: {e}")
    
    async def list_models(self) -> List[str]:
        """
        List available models in Ollama
        
        Returns:
            List of model names
        """
        async with httpx.AsyncClient(timeout=30.0) as client:
            try:
                response = await client.get(f"{self.base_url}/api/tags")
                response.raise_for_status()
                
                data = response.json()
                models = data.get("models", [])
                return [model.get("name", "") for model in models]
                
            except httpx.HTTPError as e:
                raise ValueError(f"Failed to list models: {e}")
            except Exception as e:
                raise ValueError(f"Unexpected error listing models: {e}")
    
    async def check_model_available(self, model: str) -> bool:
        """
        Check if a specific model is available
        
        Args:
            model: Model name to check
            
        Returns:
            True if model is available, False otherwise
        """
        try:
            available_models = await self.list_models()
            return model in available_models
        except Exception:
            return False
    
    async def pull_model(self, model: str) -> bool:
        """
        Pull/download a model from Ollama library
        
        Args:
            model: Model name to pull
            
        Returns:
            True if successful, False otherwise
        """
        async with httpx.AsyncClient(timeout=600.0) as client:
            try:
                response = await client.post(
                    f"{self.base_url}/api/pull",
                    json={"name": model}
                )
                response.raise_for_status()
                return True
            except Exception:
                return False
    
    async def health_check(self) -> bool:
        """
        Check if Ollama service is healthy
        
        Returns:
            True if healthy, False otherwise
        """
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(f"{self.base_url}/")
                return response.status_code == 200
        except Exception:
            return False