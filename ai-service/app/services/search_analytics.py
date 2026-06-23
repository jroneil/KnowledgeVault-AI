"""
Search analytics and logging service.
Tracks search performance, usage patterns, and provides insights.
"""

from typing import List, Dict, Optional
from datetime import datetime, timedelta
from collections import defaultdict
import statistics


class SearchEvent:
    """Represents a single search event."""
    
    def __init__(
        self,
        query: str,
        search_type: str,
        num_results: int,
        response_time_ms: float,
        user_id: Optional[str] = None,
        cache_hit: bool = False,
        error: Optional[str] = None,
        model_used: Optional[str] = None
    ):
        self.query = query
        self.search_type = search_type
        self.num_results = num_results
        self.response_time_ms = response_time_ms
        self.user_id = user_id
        self.cache_hit = cache_hit
        self.error = error
        self.model_used = model_used
        self.timestamp = datetime.now()
    
    def to_dict(self) -> Dict:
        """Convert to dictionary."""
        return {
            'query': self.query,
            'search_type': self.search_type,
            'num_results': self.num_results,
            'response_time_ms': self.response_time_ms,
            'user_id': self.user_id,
            'cache_hit': self.cache_hit,
            'error': self.error,
            'model_used': self.model_used,
            'timestamp': self.timestamp.isoformat()
        }


class SearchAnalytics:
    """
    Service for tracking and analyzing search events.
    """
    
    def __init__(self, max_events: int = 10000):
        """
        Initialize search analytics.
        
        Args:
            max_events: Maximum number of events to keep in memory
        """
        self.events: List[SearchEvent] = []
        self.max_events = max_events
    
    def log_search(self, event: SearchEvent) -> None:
        """
        Log a search event.
        
        Args:
            event: Search event to log
        """
        self.events.append(event)
        
        # Keep only the most recent events
        if len(self.events) > self.max_events:
            self.events = self.events[-self.max_events:]
    
    def get_stats(
        self,
        time_range: Optional[timedelta] = None
    ) -> Dict:
        """
        Get search statistics.
        
        Args:
            time_range: Time range to filter events (optional)
            
        Returns:
            Dictionary with search statistics
        """
        filtered_events = self._filter_events_by_time(time_range)
        
        if not filtered_events:
            return self._empty_stats()
        
        # Basic counts
        total_searches = len(filtered_events)
        successful_searches = len([e for e in filtered_events if not e.error])
        failed_searches = len([e for e in filtered_events if e.error])
        cache_hits = len([e for e in filtered_events if e.cache_hit])
        
        # Response time statistics
        response_times = [e.response_time_ms for e in filtered_events if e.response_time_ms]
        avg_response_time = statistics.mean(response_times) if response_times else 0
        median_response_time = statistics.median(response_times) if response_times else 0
        p95_response_time = self._percentile(response_times, 95) if response_times else 0
        
        # Results statistics
        result_counts = [e.num_results for e in filtered_events if e.num_results is not None]
        avg_results = statistics.mean(result_counts) if result_counts else 0
        median_results = statistics.median(result_counts) if result_counts else 0
        
        # Search type distribution
        search_type_counts = defaultdict(int)
        for event in filtered_events:
            search_type_counts[event.search_type] += 1
        
        # Model usage
        model_counts = defaultdict(int)
        for event in filtered_events:
            if event.model_used:
                model_counts[event.model_used] += 1
        
        # Error analysis
        error_counts = defaultdict(int)
        for event in filtered_events:
            if event.error:
                error_counts[event.error] += 1
        
        return {
            'time_range': {
                'start': filtered_events[0].timestamp.isoformat(),
                'end': filtered_events[-1].timestamp.isoformat()
            },
            'total_searches': total_searches,
            'successful_searches': successful_searches,
            'failed_searches': failed_searches,
            'success_rate': successful_searches / total_searches if total_searches > 0 else 0,
            'cache_hits': cache_hits,
            'cache_hit_rate': cache_hits / total_searches if total_searches > 0 else 0,
            'response_time': {
                'avg_ms': avg_response_time,
                'median_ms': median_response_time,
                'p95_ms': p95_response_time
            },
            'results': {
                'avg': avg_results,
                'median': median_results
            },
            'search_types': dict(search_type_counts),
            'models_used': dict(model_counts),
            'errors': dict(error_counts)
        }
    
    def get_top_queries(
        self,
        limit: int = 10,
        time_range: Optional[timedelta] = None
    ) -> List[Dict]:
        """
        Get most frequently searched queries.
        
        Args:
            limit: Number of top queries to return
            time_range: Time range to filter events (optional)
            
        Returns:
            List of query dictionaries with counts
        """
        filtered_events = self._filter_events_by_time(time_range)
        
        query_counts = defaultdict(int)
        for event in filtered_events:
            query_counts[event.query] += 1
        
        sorted_queries = sorted(
            query_counts.items(),
            key=lambda x: x[1],
            reverse=True
        )
        
        return [
            {
                'query': query,
                'count': count,
                'percentage': count / len(filtered_events) * 100 if filtered_events else 0
            }
            for query, count in sorted_queries[:limit]
        ]
    
    def get_slow_queries(
        self,
        limit: int = 10,
        time_range: Optional[timedelta] = None,
        threshold_ms: float = 1000
    ) -> List[Dict]:
        """
        Get queries with slow response times.
        
        Args:
            limit: Number of slow queries to return
            time_range: Time range to filter events (optional)
            threshold_ms: Response time threshold in milliseconds
            
        Returns:
            List of slow query dictionaries
        """
        filtered_events = self._filter_events_by_time(time_range)
        
        slow_queries = [
            event for event in filtered_events
            if event.response_time_ms and event.response_time_ms > threshold_ms
        ]
        
        slow_queries.sort(key=lambda x: x.response_time_ms, reverse=True)
        
        return [
            {
                'query': event.query,
                'response_time_ms': event.response_time_ms,
                'search_type': event.search_type,
                'timestamp': event.timestamp.isoformat()
            }
            for event in slow_queries[:limit]
        ]
    
    def get_search_type_performance(
        self,
        time_range: Optional[timedelta] = None
    ) -> Dict[str, Dict]:
        """
        Get performance metrics by search type.
        
        Args:
            time_range: Time range to filter events (optional)
            
        Returns:
            Dictionary mapping search types to performance metrics
        """
        filtered_events = self._filter_events_by_time(time_range)
        
        # Group events by search type
        events_by_type = defaultdict(list)
        for event in filtered_events:
            events_by_type[event.search_type].append(event)
        
        # Calculate metrics for each search type
        performance = {}
        for search_type, events in events_by_type.items():
            response_times = [e.response_time_ms for e in events if e.response_time_ms]
            result_counts = [e.num_results for e in events if e.num_results is not None]
            
            performance[search_type] = {
                'count': len(events),
                'avg_response_time_ms': statistics.mean(response_times) if response_times else 0,
                'median_response_time_ms': statistics.median(response_times) if response_times else 0,
                'avg_results': statistics.mean(result_counts) if result_counts else 0,
                'cache_hit_rate': len([e for e in events if e.cache_hit]) / len(events) if events else 0
            }
        
        return performance
    
    def get_hourly_search_counts(
        self,
        time_range: Optional[timedelta] = None
    ) -> List[Dict]:
        """
        Get search counts grouped by hour.
        
        Args:
            time_range: Time range to filter events (optional)
            
        Returns:
            List of dictionaries with hourly counts
        """
        filtered_events = self._filter_events_by_time(time_range)
        
        hourly_counts = defaultdict(int)
        for event in filtered_events:
            hour_key = event.timestamp.strftime('%Y-%m-%d %H:00')
            hourly_counts[hour_key] += 1
        
        sorted_hours = sorted(hourly_counts.items())
        
        return [
            {
                'hour': hour,
                'count': count
            }
            for hour, count in sorted_hours
        ]
    
    def _filter_events_by_time(
        self,
        time_range: Optional[timedelta]
    ) -> List[SearchEvent]:
        """Filter events by time range."""
        if time_range is None:
            return self.events
        
        cutoff_time = datetime.now() - time_range
        return [e for e in self.events if e.timestamp >= cutoff_time]
    
    def _empty_stats(self) -> Dict:
        """Return empty statistics dictionary."""
        return {
            'time_range': None,
            'total_searches': 0,
            'successful_searches': 0,
            'failed_searches': 0,
            'success_rate': 0,
            'cache_hits': 0,
            'cache_hit_rate': 0,
            'response_time': {
                'avg_ms': 0,
                'median_ms': 0,
                'p95_ms': 0
            },
            'results': {
                'avg': 0,
                'median': 0
            },
            'search_types': {},
            'models_used': {},
            'errors': {}
        }
    
    @staticmethod
    def _percentile(data: List[float], percentile: float) -> float:
        """Calculate percentile of data."""
        if not data:
            return 0
        data_sorted = sorted(data)
        k = (len(data_sorted) - 1) * percentile / 100
        f = int(k)
        c = f + 1 if c < len(data_sorted) else f
        return data_sorted[f] if f == c else data_sorted[f] * (c - k) + data_sorted[c] * k - f
    
    def clear_old_events(self, max_age: timedelta) -> int:
        """
        Clear events older than max_age.
        
        Args:
            max_age: Maximum age of events to keep
            
        Returns:
            Number of events removed
        """
        cutoff_time = datetime.now() - max_age
        original_count = len(self.events)
        self.events = [e for e in self.events if e.timestamp >= cutoff_time]
        return original_count - len(self.events)


# Global search analytics instance
search_analytics = SearchAnalytics()