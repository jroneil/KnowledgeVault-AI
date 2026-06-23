"""
Distributed caching service using Redis.
Provides high-performance caching for search results, embeddings, and API responses.
"""

import json
import pickle
import hashlib
from typing import Optional, Any, Dict, List
import redis.asyncio as redis
from datetime import timedelta
import logging

from app.core.config import settings

logger = logging.getLogger(__name__)


class RedisCacheService:
    """Distributed caching service using Redis."""
    
    def __init__(self):
        """Initialize Redis cache service."""
        self.redis_url = settings.REDIS_URL if hasattr(settings, 'REDIS_URL') else "redis://localhost:6379/0"
        self.ttl = settings.REDIS_TTL if hasattr(settings, 'REDIS_TTL') else 3600
        self.redis: Optional[redis.Redis] = None
        self._connected = False
    
    async def connect(self) -> bool:
        """Connect to Redis server."""
        try:
            self.redis = redis.from_url(
                self.redis_url,
                encoding="utf-8",
                decode_responses=False,  # Handle binary data for pickling
                socket_timeout=5,
                socket_connect_timeout=5
            )
            
            # Test connection
            await self.redis.ping()
            self._connected = True
            logger.info("Connected to Redis cache")
            return True
            
        except Exception as e:
            logger.error(f"Failed to connect to Redis: {str(e)}")
            self._connected = False
            return False
    
    async def disconnect(self):
        """Disconnect from Redis server."""
        if self.redis:
            await self.redis.close()
            self._connected = False
            logger.info("Disconnected from Redis cache")
    
    async def cache_search_results(
        self,
        key: str,
        results: Any,
        ttl: Optional[int] = None
    ) -> bool:
        """
        Cache search results.
        
        Args:
            key: Cache key
            results: Results to cache (any serializable object)
            ttl: Time to live in seconds (defaults to REDIS_TTL)
        
        Returns:
            True if cached successfully
        """
        if not self._connected:
            return False
        
        try:
            serialized = pickle.dumps(results)
            await self.redis.setex(
                f"search:{key}",
                ttl or self.ttl,
                serialized
            )
            logger.debug(f"Cached search results: {key}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache search results: {str(e)}")
            return False
    
    async def get_cached_search_results(self, key: str) -> Optional[Any]:
        """
        Get cached search results.
        
        Args:
            key: Cache key
        
        Returns:
            Cached results or None if not found
        """
        if not self._connected:
            return None
        
        try:
            cached = await self.redis.get(f"search:{key}")
            if cached:
                logger.debug(f"Cache hit for search results: {key}")
                return pickle.loads(cached)
            logger.debug(f"Cache miss for search results: {key}")
            return None
            
        except Exception as e:
            logger.error(f"Failed to get cached search results: {str(e)}")
            return None
    
    async def cache_embedding(
        self,
        text_hash: str,
        embedding: List[float],
        ttl: int = 86400
    ) -> bool:
        """
        Cache text embedding for 24 hours.
        
        Args:
            text_hash: Hash of the text content
            embedding: Embedding vector
            ttl: Time to live in seconds (default: 24 hours)
        
        Returns:
            True if cached successfully
        """
        if not self._connected:
            return False
        
        try:
            await self.redis.setex(
                f"embedding:{text_hash}",
                ttl,
                json.dumps(embedding)
            )
            logger.debug(f"Cached embedding: {text_hash}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache embedding: {str(e)}")
            return False
    
    async def get_cached_embedding(
        self,
        text_hash: str
    ) -> Optional[List[float]]:
        """
        Get cached embedding.
        
        Args:
            text_hash: Hash of the text content
        
        Returns:
            Cached embedding or None if not found
        """
        if not self._connected:
            return None
        
        try:
            cached = await self.redis.get(f"embedding:{text_hash}")
            if cached:
                logger.debug(f"Cache hit for embedding: {text_hash}")
                return json.loads(cached)
            logger.debug(f"Cache miss for embedding: {text_hash}")
            return None
            
        except Exception as e:
            logger.error(f"Failed to get cached embedding: {str(e)}")
            return None
    
    def generate_text_hash(self, text: str) -> str:
        """
        Generate hash for text content.
        
        Args:
            text: Text to hash
        
        Returns:
            SHA256 hash of the text
        """
        return hashlib.sha256(text.encode()).hexdigest()[:32]
    
    async def cache_api_response(
        self,
        key: str,
        response: Any,
        ttl: Optional[int] = None
    ) -> bool:
        """
        Cache API response.
        
        Args:
            key: Cache key
            response: Response to cache
            ttl: Time to live in seconds
        
        Returns:
            True if cached successfully
        """
        if not self._connected:
            return False
        
        try:
            serialized = pickle.dumps(response)
            await self.redis.setex(
                f"api:{key}",
                ttl or self.ttl,
                serialized
            )
            logger.debug(f"Cached API response: {key}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache API response: {str(e)}")
            return False
    
    async def get_cached_api_response(self, key: str) -> Optional[Any]:
        """
        Get cached API response.
        
        Args:
            key: Cache key
        
        Returns:
            Cached response or None if not found
        """
        if not self._connected:
            return None
        
        try:
            cached = await self.redis.get(f"api:{key}")
            if cached:
                logger.debug(f"Cache hit for API response: {key}")
                return pickle.loads(cached)
            return None
            
        except Exception as e:
            logger.error(f"Failed to get cached API response: {str(e)}")
            return None
    
    async def invalidate_pattern(self, pattern: str) -> int:
        """
        Invalidate keys matching pattern.
        
        Args:
            pattern: Key pattern (e.g., "search:*")
        
        Returns:
            Number of keys invalidated
        """
        if not self._connected:
            return 0
        
        try:
            keys = []
            async for key in self.redis.scan_iter(match=pattern):
                keys.append(key)
            
            if keys:
                deleted = await self.redis.delete(*keys)
                logger.info(f"Invalidated {deleted} keys matching pattern: {pattern}")
                return deleted
            return 0
            
        except Exception as e:
            logger.error(f"Failed to invalidate pattern: {str(e)}")
            return 0
    
    async def invalidate_document_cache(self, document_id: int) -> int:
        """
        Invalidate all cache entries for a document.
        
        Args:
            document_id: Document ID
        
        Returns:
            Number of keys invalidated
        """
        patterns = [
            f"search:*:{document_id}:*",
            f"api:*:{document_id}:*"
        ]
        
        total_deleted = 0
        for pattern in patterns:
            total_deleted += await self.invalidate_pattern(pattern)
        
        return total_deleted
    
    async def clear_all_cache(self) -> bool:
        """
        Clear all cache entries.
        
        Returns:
            True if cleared successfully
        """
        if not self._connected:
            return False
        
        try:
            await self.redis.flushdb()
            logger.info("Cleared all cache entries")
            return True
            
        except Exception as e:
            logger.error(f"Failed to clear cache: {str(e)}")
            return False
    
    async def get_cache_stats(self) -> Dict:
        """
        Get cache statistics.
        
        Returns:
            Dictionary with cache statistics
        """
        if not self._connected:
            return {
                "status": "disconnected",
                "error": "Redis not connected"
            }
        
        try:
            info = await self.redis.info()
            keyspace = info.get('db0', {})
            
            stats = {
                "status": "connected",
                "used_memory_human": info.get("used_memory_human", "N/A"),
                "used_memory_bytes": info.get("used_memory", 0),
                "total_keys": keyspace.get("keys", 0),
                "keyspace_hits": info.get("keyspace_hits", 0),
                "keyspace_misses": info.get("keyspace_misses", 0),
                "connected_clients": info.get("connected_clients", 0),
                "uptime_in_seconds": info.get("uptime_in_seconds", 0),
                "total_commands_processed": info.get("total_commands_processed", 0)
            }
            
            # Calculate hit rate
            hits = stats["keyspace_hits"]
            misses = stats["keyspace_misses"]
            total = hits + misses
            stats["hit_rate"] = round(hits / total, 4) if total > 0 else 0.0
            
            return stats
            
        except Exception as e:
            logger.error(f"Failed to get cache stats: {str(e)}")
            return {
                "status": "error",
                "error": str(e)
            }
    
    async def get_keys_by_pattern(self, pattern: str, limit: int = 100) -> List[str]:
        """
        Get keys matching pattern.
        
        Args:
            pattern: Key pattern
            limit: Maximum number of keys to return
        
        Returns:
            List of matching keys
        """
        if not self._connected:
            return []
        
        try:
            keys = []
            async for key in self.redis.scan_iter(match=pattern, count=limit):
                keys.append(key.decode() if isinstance(key, bytes) else key)
                if len(keys) >= limit:
                    break
            return keys
            
        except Exception as e:
            logger.error(f"Failed to get keys by pattern: {str(e)}")
            return []
    
    async def delete_keys(self, keys: List[str]) -> int:
        """
        Delete specific keys.
        
        Args:
            keys: List of keys to delete
        
        Returns:
            Number of keys deleted
        """
        if not self._connected or not keys:
            return 0
        
        try:
            return await self.redis.delete(*keys)
            
        except Exception as e:
            logger.error(f"Failed to delete keys: {str(e)}")
            return 0
    
    async def is_connected(self) -> bool:
        """
        Check if Redis is connected.
        
        Returns:
            True if connected
        """
        if not self._connected:
            return False
        
        try:
            await self.redis.ping()
            return True
            
        except Exception:
            self._connected = False
            return False
    
    async def set_with_retry(
        self,
        key: str,
        value: Any,
        ttl: Optional[int] = None,
        retries: int = 3
    ) -> bool:
        """
        Set key with value with retry logic.
        
        Args:
            key: Cache key
            value: Value to cache
            ttl: Time to live in seconds
            retries: Number of retries
        
        Returns:
            True if set successfully
        """
        for attempt in range(retries):
            try:
                serialized = pickle.dumps(value)
                if ttl:
                    await self.redis.setex(key, ttl, serialized)
                else:
                    await self.redis.set(key, serialized)
                return True
                
            except Exception as e:
                if attempt == retries - 1:
                    logger.error(f"Failed to set key after {retries} attempts: {str(e)}")
                    return False
                await asyncio.sleep(0.1 * (attempt + 1))
        
        return False


# Global Redis cache service instance
redis_cache_service = RedisCacheService()