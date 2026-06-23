"""
Result re-ranking service to improve search relevance.
Uses cross-encoder models to re-rank search results based on query-document relevance.
"""

from typing import List, Dict, Tuple
import asyncio

from app.services.ollama_client import ollama_client


class ReRankingService:
    """
    Service for re-ranking search results using cross-encoder models.
    """
    
    async def rerank_results(
        self,
        query: str,
        results: List[Dict],
        top_k: int = 10,
        method: str = "llm"
    ) -> List[Dict]:
        """
        Re-rank search results based on query relevance.
        
        Args:
            query: Original search query
            results: List of search results to re-rank
            top_k: Number of top results to return
            method: Re-ranking method ("llm", "similarity", "hybrid")
            
        Returns:
            Re-ranked list of results
        """
        if not results:
            return results
        
        if len(results) <= 1:
            return results[:top_k]
        
        if method == "llm":
            return await self._llm_rerank(query, results, top_k)
        elif method == "similarity":
            return self._similarity_rerank(results, top_k)
        elif method == "hybrid":
            return await self._hybrid_rerank(query, results, top_k)
        else:
            return await self._llm_rerank(query, results, top_k)
    
    async def _llm_rerank(
        self,
        query: str,
        results: List[Dict],
        top_k: int
    ) -> List[Dict]:
        """
        Use LLM to re-rank results based on query relevance.
        
        This is computationally expensive but provides high-quality re-ranking.
        """
        # Limit the number of results to send to LLM to avoid context limits
        max_results_for_llm = min(len(results), 20)
        results_to_rerank = results[:max_results_for_llm]
        
        # Prepare results for LLM
        results_text = ""
        for idx, result in enumerate(results_to_rerank):
            chunk = result.get('chunk')
            if chunk:
                content = chunk.content[:500]  # Truncate for LLM context
                results_text += f"\n[{idx}] {content}\n"
        
        system_prompt = """You are a search relevance evaluator. Your task is to rank search results by their relevance to a query.

Given a search query and a list of document chunks, rate each chunk's relevance on a scale of 0.0 to 1.0:
- 1.0 = Highly relevant, directly answers the query
- 0.7-0.9 = Relevant, contains useful information
- 0.4-0.6 = Somewhat relevant, tangentially related
- 0.0-0.3 = Not relevant, unrelated to the query

Return ONLY a JSON array of relevance scores in the same order as the input chunks, with no additional text.

Example:
Input chunks: [chunk1, chunk2, chunk3]
Output: [0.9, 0.3, 0.7]
"""
        
        user_message = f"""
Query: {query}

Document chunks to rank:{results_text}

Provide relevance scores for each chunk:
"""
        
        try:
            scores_text = await ollama_client.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_message}
                ],
                model="qwen2:7b"
            )
            
            # Parse scores
            import json
            try:
                scores = json.loads(scores_text)
                if not isinstance(scores, list):
                    scores = []
            except json.JSONDecodeError:
                # Fallback: extract numbers from response
                import re
                scores = [float(x) for x in re.findall(r'\d+\.?\d*', scores_text)]
            
            # Assign scores to results
            for idx, result in enumerate(results_to_rerank):
                if idx < len(scores):
                    result['rerank_score'] = scores[idx]
                else:
                    result['rerank_score'] = 0.5  # Default score
            
            # Sort by re-rank score
            reranked = sorted(results_to_rerank, key=lambda x: x.get('rerank_score', 0.5), reverse=True)
            
            # Include remaining results (not re-ranked)
            remaining_results = results[max_results_for_llm:]
            final_results = reranked[:top_k] + remaining_results
            
            return final_results[:top_k]
            
        except Exception as e:
            # Fallback: return original results sorted by existing scores
            print(f"LLM re-ranking failed: {e}")
            return self._similarity_rerank(results, top_k)
    
    def _similarity_rerank(
        self,
        results: List[Dict],
        top_k: int
    ) -> List[Dict]:
        """
        Re-rank using existing similarity/vector scores.
        Fast but less sophisticated than LLM re-ranking.
        """
        # Sort by existing scores (vector_score, keyword_score, or combined_score)
        sorted_results = sorted(
            results,
            key=lambda x: x.get('combined_score', x.get('vector_score', x.get('keyword_score', 0))),
            reverse=True
        )
        
        return sorted_results[:top_k]
    
    async def _hybrid_rerank(
        self,
        query: str,
        results: List[Dict],
        top_k: int
    ) -> List[Dict]:
        """
        Combine LLM re-ranking for top results with similarity-based ranking for rest.
        Balance between quality and performance.
        """
        if len(results) <= 5:
            # For small result sets, use LLM for all
            return await self._llm_rerank(query, results, top_k)
        
        # LLM re-rank top 10 results
        top_results = results[:10]
        reranked_top = await self._llm_rerank(query, top_results, len(top_results))
        
        # Similarity re-rank remaining results
        remaining_results = results[10:]
        reranked_remaining = self._similarity_rerank(remaining_results, len(remaining_results))
        
        # Combine results
        final_results = reranked_top + reranked_remaining
        return final_results[:top_k]
    
    async def diversify_results(
        self,
        results: List[Dict],
        max_results: int = 10,
        diversity_threshold: float = 0.8
    ) -> List[Dict]:
        """
        Diversify search results to avoid redundant content.
        
        Uses content similarity to filter out similar results.
        """
        if not results:
            return results
        
        diversified = [results[0]]
        
        for result in results[1:]:
            if len(diversified) >= max_results:
                break
            
            current_content = result.get('chunk', {}).get('content', '').lower()
            is_similar = False
            
            for existing in diversified:
                existing_content = existing.get('chunk', {}).get('content', '').lower()
                
                # Simple similarity check based on word overlap
                similarity = self._calculate_content_similarity(current_content, existing_content)
                
                if similarity > diversity_threshold:
                    is_similar = True
                    break
            
            if not is_similar:
                diversified.append(result)
        
        return diversified
    
    def _calculate_content_similarity(
        self,
        content1: str,
        content2: str,
        n_words: int = 20
    ) -> float:
        """
        Calculate content similarity based on n-gram overlap.
        """
        words1 = set(content1.split())
        words2 = set(content2.split())
        
        if not words1 or not words2:
            return 0.0
        
        # Jaccard similarity
        intersection = len(words1 & words2)
        union = len(words1 | words2)
        
        return intersection / union if union > 0 else 0.0
    
    async def enhance_results_with_metadata(
        self,
        results: List[Dict]
    ) -> List[Dict]:
        """
        Enhance search results with additional metadata and context.
        """
        for result in results:
            chunk = result.get('chunk')
            if chunk:
                result['enhanced'] = {
                    'word_count': len(chunk.content.split()),
                    'char_count': len(chunk.content),
                    'has_page_number': chunk.page_number is not None,
                    'has_section': chunk.section_name is not None,
                    'is_long_chunk': len(chunk.content) > 1000,
                    'is_short_chunk': len(chunk.content) < 200
                }
        
        return results


# Global re-ranking service instance
reranker_service = ReRankingService()