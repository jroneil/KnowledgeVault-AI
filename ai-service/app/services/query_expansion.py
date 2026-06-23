"""
Query expansion service to improve search relevance.
Expands user queries with related terms and rewrites queries for better matching.
"""

from typing import List, Dict
from app.services.ollama_client import ollama_client


class QueryExpansionService:
    """
    Service for expanding and rewriting search queries to improve relevance.
    """
    
    async def expand_query(
        self,
        original_query: str,
        method: str = "llm",
        max_expansions: int = 3
    ) -> Dict:
        """
        Expand the query using the specified method.
        
        Args:
            original_query: The original search query
            method: Expansion method ("llm", "synonyms", "related")
            max_expansions: Maximum number of expanded queries to generate
            
        Returns:
            Dictionary with original query and expanded queries
        """
        if method == "llm":
            return await self._llm_expansion(original_query, max_expansions)
        elif method == "synonyms":
            return self._synonym_expansion(original_query, max_expansions)
        elif method == "related":
            return await self._related_terms_expansion(original_query, max_expansions)
        else:
            # Default to LLM expansion
            return await self._llm_expansion(original_query, max_expansions)
    
    async def _llm_expansion(
        self,
        query: str,
        max_expansions: int
    ) -> Dict:
        """
        Use LLM to generate expanded query variations.
        
        This is the most sophisticated method, using the LLM's understanding
        of semantics to generate relevant query variations.
        """
        system_prompt = """You are a search query assistant. Your task is to expand a search query with relevant alternative phrasings and related terms.

Given a user's search query, generate 2-3 expanded queries that:
1. Rephrase the question in different ways
2. Include relevant synonyms and related concepts
3. Add context-specific terminology
4. Maintain the original intent

Return ONLY a JSON array of expanded query strings, with no additional text or explanation.

Example:
Input: "How do I configure database connections?"
Output: ["database connection setup guide", "configure DB connection settings", "how to set up database connections"]
"""
        
        user_message = f"Expand this search query: {query}"
        
        try:
            expanded_queries = await ollama_client.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_message}
                ],
                model="qwen2:7b"
            )
            
            # Parse the response to extract expanded queries
            # The LLM should return a JSON array
            import json
            try:
                queries = json.loads(expanded_queries)
                if not isinstance(queries, list):
                    queries = [queries]
                queries = queries[:max_expansions]
            except json.JSONDecodeError:
                # Fallback: split by common delimiters
                queries = [
                    q.strip().strip('"\'')
                    for q in expanded_queries.replace('\n', ',').split(',')
                    if q.strip()
                ][:max_expansions]
            
            return {
                "original_query": query,
                "expanded_queries": queries,
                "method": "llm"
            }
            
        except Exception as e:
            # Fallback to original query if LLM fails
            return {
                "original_query": query,
                "expanded_queries": [],
                "method": "llm",
                "error": str(e)
            }
    
    def _synonym_expansion(
        self,
        query: str,
        max_expansions: int
    ) -> Dict:
        """
        Expand query using a built-in synonym dictionary.
        Faster than LLM but less sophisticated.
        """
        # Built-in synonym dictionary for common technical terms
        synonyms = {
            "configure": ["setup", "set up", "configure settings", "configuration"],
            "database": ["db", "data store", "storage", "data storage"],
            "connection": ["connectivity", "link", "interface", "integration"],
            "security": ["protection", "safety", "security measures", "authentication"],
            "api": ["interface", "endpoint", "web service", "REST API"],
            "deploy": ["deployment", "deploying", "installation", "install"],
            "upload": ["file upload", "transfer", "send", "post"],
            "document": ["file", "record", "paperwork", "documentation"],
            "search": ["find", "locate", "look up", "query"],
            "version": ["revision", "release", "update", "edition"],
            "collection": ["folder", "group", "set", "category"],
            "user": ["account", "member", "person", "profile"]
        }
        
        expanded_queries = []
        query_lower = query.lower()
        
        # Find words in the query that have synonyms
        for word, word_synonyms in synonyms.items():
            if word in query_lower:
                for synonym in word_synonyms[:max_expansions]:
                    # Replace the word with synonym
                    expanded_query = query_lower.replace(word, synonym, 1)
                    if expanded_query != query_lower:
                        expanded_queries.append(expanded_query)
        
        return {
            "original_query": query,
            "expanded_queries": expanded_queries[:max_expansions],
            "method": "synonyms"
        }
    
    async def _related_terms_expansion(
        self,
        query: str,
        max_expansions: int
    ) -> Dict:
        """
        Expand query by asking LLM for related terms and concepts.
        """
        system_prompt = """You are a search assistant. Given a search query, identify 2-3 key concepts or terms that are closely related to the query.

Return ONLY a JSON array of related terms/phrases, with no additional text.

Example:
Input: "How do I configure database connections?"
Output: ["database drivers", "connection strings", "pooling configuration"]
"""
        
        try:
            related_terms = await ollama_client.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": query}
                ],
                model="qwen2:7b"
            )
            
            import json
            try:
                terms = json.loads(related_terms)
                if not isinstance(terms, list):
                    terms = [terms]
                
                # Combine original query with each related term
                expanded_queries = [f"{query} {term}" for term in terms[:max_expansions]]
            except json.JSONDecodeError:
                expanded_queries = []
            
            return {
                "original_query": query,
                "expanded_queries": expanded_queries,
                "method": "related"
            }
            
        except Exception as e:
            return {
                "original_query": query,
                "expanded_queries": [],
                "method": "related",
                "error": str(e)
            }
    
    async def rewrite_query_for_search(
        self,
        query: str,
        search_type: str = "semantic"
    ) -> str:
        """
        Rewrite a query to optimize it for the specific search type.
        
        Args:
            query: Original user query
            search_type: "semantic" or "keyword"
            
        Returns:
            Rewritten query optimized for the search type
        """
        if search_type == "semantic":
            # For semantic search, keep the natural language but add context
            return await self._rewrite_for_semantic(query)
        else:
            # For keyword search, focus on key terms
            return await self._rewrite_for_keyword(query)
    
    async def _rewrite_for_semantic(self, query: str) -> str:
        """
        Rewrite query for semantic search - keep natural language but add context.
        """
        # For semantic search, the natural language query is usually optimal
        # Just ensure it's a complete question/statement
        if not query.endswith('?') and not query.endswith('.'):
            # Add context if it's a fragment
            if len(query.split()) < 4:
                return f"Information about {query}"
        return query
    
    async def _rewrite_for_keyword(self, query: str) -> str:
        """
        Rewrite query for keyword search - focus on important terms.
        """
        system_prompt = """Extract the most important keywords from a search query for a keyword-based search system.

Remove stop words, articles, and common words. Keep technical terms, proper nouns, and important concepts.

Return ONLY the extracted keywords as a space-separated string, no additional text.

Example:
Input: "How do I configure database connections?"
Output: "configure database connections"
"""
        
        try:
            keywords = await ollama_client.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": query}
                ],
                model="qwen2:7b"
            )
            return keywords.strip()
        except Exception:
            # Fallback: remove common stop words
            stop_words = {'the', 'a', 'an', 'is', 'are', 'was', 'were', 'be', 'been', 
                         'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would',
                         'how', 'to', 'for', 'in', 'on', 'at', 'by', 'from', 'with'}
            words = query.lower().split()
            keywords = [w for w in words if w not in stop_words]
            return ' '.join(keywords)


# Global query expansion service instance
query_expansion_service = QueryExpansionService()