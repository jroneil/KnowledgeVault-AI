"""
Caching service for search results and embeddings.
Improves performance by caching frequently accessed data.
"""

import hashlib
import json
from typing import Any, Optional, Dict, List
from datetime import datetime, timedelta
from functools import wraps


class CacheService:
    """
    Simple in-memory cache service for search results and embeddings.
    """
    
    def __init__(self, default_ttl: int = 3600):
        """
        Initialize cache service.
        
        Args:
            default_ttl: Default time-to-live in seconds (default: 1 hour)
        """
        self.cache: Dict[str, Dict[str, Any]] = {}
        self.default_ttl = default_ttl
        self.max_size = 1000  # Maximum number of cache entries
    
    def _generate_key(self, *args, **kwargs) -> str:
        """
        Generate a cache key from function arguments.
        """
        # Create a deterministic string representation
        key_parts = [str(arg) for arg in args]
        key_parts.extend([f"{k}={v}" for k, v in sorted(kwargs.items())])
        key_string = "|".join(key_parts)
        
        # Hash the key string for consistent length
        return hashlib.sha256(key_string.encode()).hexdigest()
    
    def get(self, key: str) -> Optional[Any]:
        """
        Get a value from the cache.
        
        Args:
            key: Cache key
            
        Returns:
            Cached value or None if not found or expired
        """
        if key not in self.cache:
            return None
        
        entry = self.cache[key]
        
        # Check if expired
        if datetime.now() > entry['expires_at']:
            del self.cache[key]
            return None
        
        # Update access time and hit count
        entry['last_access'] = datetime.now()
        entry['hits'] += 1
        
        return entry['value']
    
    def set(self, key: str, value: Any, ttl: Optional[int] = None) -> None:
        """
        Set a value in the cache.
        
        Args:
            key: Cache key
            value: Value to cache
            ttl: Time-to-live in seconds (uses default if not provided)
        """
        if len(self.cache) >= self.max_size:
            self._evict_lru()
        
        ttl = ttl or self.default_ttl
        expires_at = datetime.now() + timedelta(seconds=ttl)
        
        self.cache[key] = {
            'value': value,
            'expires_at': expires_at,
            'created_at': datetime.now(),
            'last_access': datetime.now(),
            'hits': 0
        }
    
    def invalidate(self, key: str) -> bool:
        """
        Invalidate a specific cache entry.
        
        Args:
            key: Cache key to invalidate
            
        Returns:
            True if entry was invalidated, False if not found
        """
        if key in self.cache:
            del self.cache[key]
            return True
        return False
    
    def invalidate_pattern(self, pattern: str) -> int:
        """
        Invalidate all cache entries matching a pattern.
        
        Args:
            pattern: Pattern to match (simple substring matching)
            
        Returns:
            Number of entries invalidated
        """
        keys_to_delete = [key for key in self.cache.keys() if pattern in key]
        
        for key in keys_to_delete:
            del self.cache[key]
        
        return len(keys_to_delete)
    
    def clear(self) -> None:
        """Clear all cache entries."""
        self.cache.clear()
    
    def _evict_lru(self) -> None:
        """Evict least recently used cache entry."""
        if not self.cache:
            return
        
        # Find the least recently used entry
        lru_key = min(
            self.cache.keys(),
            key=lambda k: self.cache[k]['last_access']
        )
        
        del self.cache[lru_key]
    
    def get_stats(self) -> Dict[str, Any]:
        """
        Get cache statistics.
        
        Returns:
            Dictionary with cache statistics
        """
        if not self.cache:
            return {
                'size': 0,
                'hits': 0,
                'entries': []
            }
        
        total_hits = sum(entry['hits'] for entry in self.cache.values())
        
        return {
            'size': len(self.cache),
            'max_size': self.max_size,
            'total_hits': total_hits,
            'avg_hits': total_hits / len(self.cache),
            'entries': [
                {
                    'key': key,
                    'created_at': entry['created_at'].isoformat(),
                    'last_access': entry['last_access'].isoformat(),
                    'expires_at': entry['expires_at'].isoformat(),
                    'hits': entry['hits'],
                    'ttl_seconds': (entry['expires_at'] - datetime.now()).total_seconds()
                }
                for key, entry in sorted(
                    self.cache.items(),
                    key=lambda x: x[1]['last_access'],
                    reverse=True
                )[:10]  # Top 10 entries
            ]
        }
    
    def cache_result(self, ttl: Optional[int] = None):
        """
        Decorator to cache function results.
        
        Args:
            ttl: Time-to-live in seconds
            
        Returns:
            Decorated function with caching
        """
        def decorator(func):
            @wraps(func)
            async def async_wrapper(*args, **kwargs):
                # Generate cache key
                key = self._generate_key(func.__name__, *args, **kwargs)
                
                # Try to get from cache
                cached_value = self.get(key)
                if cached_value is not None:
                    return cached_value
                
                # Call function and cache result
                result = await func(*args, **kwargs)
                self.set(key, result, ttl)
                
                return result
            
            @wraps(func)
            def sync_wrapper(*args, **kwargs):
                # Generate cache key
                key = self._generate_key(func.__name__, *args, **kwargs)
                
                # Try to get from cache
                cached_value = self.get(key)
                if cached_value is not None:
                    return cached_value
                
                # Call function and cache result
                result = func(*args, **kwargs)
                self.set(key, result, ttl)
                
                return result
            
            # Return appropriate wrapper based on function type
            import asyncio
            if asyncio.iscoroutinefunction(func):
                return async_wrapper
            else:
                return sync_wrapper
        
        return decorator


class SearchCache(CacheService):
    """
    Specialized cache for search results.
    """
    
    def cache_search_results(
        self,
        query: str,
        search_type: str,
        results: List[Any],
        ttl: int = 600
    ) -> None:
        """
        Cache search results.
        
        Args:
            query: Search query
            search_type: Type of search (semantic, hybrid, keyword)
            results: Search results to cache
            ttl: Time-to-live in seconds (default: 10 minutes)
        """
        key = self._generate_key('search', search_type, query.lower())
        self.set(key, results, ttl)
    
    def get_search_results(
        self,
        query: str,
        search_type: str
    ) -> Optional[List[Any]]:
        """
        Get cached search results.
        
        Args:
            query: Search query
            search_type: Type of search
            
        Returns:
            Cached results or None if not found
        """
        key = self._generate_key('search', search_type, query.lower())
        return self.get(key)
    
    def cache_embedding(
        self,
        text: str,
        model_name: str,
        embedding: List[float],
        ttl: int = 86400
    ) -> None:
        """
        Cache text embedding.
        
        Args:
            text: Input text
            model_name: Model used for embedding
            embedding: Embedding vector
            ttl: Time-to-live in seconds (default: 24 hours)
        """
        key = self._generate_key('embedding', model_name, text.lower())
        self.set(key, embedding, ttl)
    
    def get_embedding(
        self,
        text: str,
        model_name: str
    ) -> Optional[List[float]]:
        """
        Get cached embedding.
        
        Args:
            text: Input text
            model_name: Model used for embedding
            
        Returns:
            Cached embedding or None if not found
        """
        key = self._generate_key('embedding', model_name, text.lower())
        return self.get(key)


# Global cache service instance
cache_service = SearchCache(default_ttl=3600)