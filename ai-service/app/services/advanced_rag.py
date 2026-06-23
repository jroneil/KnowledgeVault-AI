"""
Advanced RAG service with citation links and improved context assembly.
Provides better source attribution and answer quality.
"""

from typing import List, Dict, Tuple, Optional
import re

from app.services.ollama_client import ollama_client
from app.services.multi_model_service import multi_model_service


class AdvancedRAGService:
    """
    Advanced Retrieval-Augmented Generation service with citation links.
    """
    
    async def generate_answer_with_citations(
        self,
        query: str,
        contexts: List[Dict],
        model_name: Optional[str] = None,
        max_tokens: Optional[int] = None,
        temperature: float = 0.7,
        include_sources: bool = True
    ) -> Dict:
        """
        Generate an answer with embedded citations.
        
        Args:
            query: User's question
            contexts: List of context chunks with metadata
            model_name: LLM model to use
            max_tokens: Maximum tokens for answer
            temperature: Temperature for generation
            include_sources: Whether to include source list
            
        Returns:
            Dictionary with answer and citations
        """
        model_name = model_name or multi_model_service.DEFAULT_LLM_MODEL
        
        # Assemble context with citation markers
        context_text = self._assemble_context_with_citations(contexts)
        
        # Generate answer with citations
        system_prompt = """You are a helpful assistant that answers questions based on provided context. 

IMPORTANT: When using information from the context, you MUST cite it using the citation format [Citation N] where N is the citation number.

Guidelines:
- Answer the question using ONLY the provided context
- Cite every statement that comes from the context
- If the context doesn't contain the answer, say so
- Use multiple citations if information comes from different sources
- Keep citations in the same sentence as the information they support
- Format citations as [1], [2], [3], etc.

Example:
"According to the documentation [1], users must have the admin role to delete documents [2]."
"""
        
        user_message = f"""
Context:
{context_text}

Question: {query}

Provide a detailed answer with proper citations:
"""
        
        try:
            answer = await multi_model_service.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_message}
                ],
                model_name=model_name,
                max_tokens=max_tokens
            )
            
            # Extract and validate citations
            citations_in_answer = self._extract_citations(answer)
            valid_citations = self._validate_citations(citations_in_answer, contexts)
            
            # Prepare response
            response = {
                'query': query,
                'answer': answer,
                'citations': valid_citations,
                'total_citations': len(valid_citations),
                'model_used': model_name,
                'sources_used': len(contexts)
            }
            
            if include_sources:
                response['sources'] = self._format_sources(contexts, valid_citations)
            
            return response
            
        except Exception as e:
            # Fallback: simple answer without citations
            return {
                'query': query,
                'answer': "I apologize, but I encountered an error generating the answer. Please try again.",
                'error': str(e),
                'model_used': model_name
            }
    
    def _assemble_context_with_citations(
        self,
        contexts: List[Dict]
    ) -> str:
        """
        Assemble context text with citation markers.
        
        Args:
            contexts: List of context chunks
            
        Returns:
            Formatted context string with citations
        """
        context_parts = []
        
        for idx, ctx in enumerate(contexts, 1):
            chunk = ctx.get('chunk', {})
            similarity = ctx.get('similarity_score', 0.0)
            
            # Format citation header
            citation_header = f"[Citation {idx}]"
            
            # Format metadata
            metadata = []
            if hasattr(chunk, 'document_id'):
                metadata.append(f"Document: {chunk.document_id}")
            if hasattr(chunk, 'page_number') and chunk.page_number:
                metadata.append(f"Page: {chunk.page_number}")
            if hasattr(chunk, 'section_name') and chunk.section_name:
                metadata.append(f"Section: {chunk.section_name}")
            if similarity > 0:
                metadata.append(f"Relevance: {similarity:.2f}")
            
            metadata_str = " | ".join(metadata) if metadata else ""
            
            # Format content
            content = chunk.content if hasattr(chunk, 'content') else str(ctx)
            
            # Combine all parts
            part = f"{citation_header}"
            if metadata_str:
                part += f" ({metadata_str})"
            part += f"\n{content}\n"
            
            context_parts.append(part)
        
        return "\n".join(context_parts)
    
    def _extract_citations(self, text: str) -> List[int]:
        """
        Extract citation numbers from text.
        
        Args:
            text: Text containing citations
            
        Returns:
            List of citation numbers
        """
        # Find all citation patterns like [1], [2], etc.
        citations = re.findall(r'\[(\d+)\]', text)
        return [int(c) for c in citations]
    
    def _validate_citations(
        self,
        citations: List[int],
        contexts: List[Dict]
    ) -> List[int]:
        """
        Validate that citations are within valid range.
        
        Args:
            citations: List of citation numbers
            contexts: List of contexts
            
        Returns:
            List of valid citation numbers
        """
        max_citation = len(contexts)
        return [c for c in citations if 1 <= c <= max_citation]
    
    def _format_sources(
        self,
        contexts: List[Dict],
        citations: List[int]
    ) -> List[Dict]:
        """
        Format source information for cited contexts.
        
        Args:
            contexts: List of all contexts
            citations: List of citation numbers used
            
        Returns:
            List of formatted source information
        """
        sources = []
        
        for citation_num in citations:
            if 1 <= citation_num <= len(contexts):
                ctx = contexts[citation_num - 1]
                chunk = ctx.get('chunk', {})
                
                source = {
                    'citation': citation_num,
                    'document_id': getattr(chunk, 'document_id', None),
                    'chunk_id': getattr(chunk, 'id', None),
                    'page_number': getattr(chunk, 'page_number', None),
                    'section_name': getattr(chunk, 'section_name', None),
                    'similarity': ctx.get('similarity_score', 0.0),
                    'content_preview': (chunk.content[:200] + '...') if hasattr(chunk, 'content') else ''
                }
                
                sources.append(source)
        
        return sources
    
    async def generate_follow_up_questions(
        self,
        query: str,
        answer: str,
        contexts: List[Dict],
        num_questions: int = 3
    ) -> List[str]:
        """
        Generate relevant follow-up questions.
        
        Args:
            query: Original query
            answer: Generated answer
            contexts: Contexts used for answer
            num_questions: Number of follow-up questions to generate
            
        Returns:
            List of follow-up questions
        """
        system_prompt = f"""You are a helpful assistant that generates relevant follow-up questions based on a user's question and the answer provided.

Generate {num_questions} relevant follow-up questions that:
1. Explore related topics mentioned in the answer
2. Dig deeper into specific points
3. Clarify unclear information
4. Cover practical applications or examples

Return ONLY a JSON array of question strings, with no additional text.

Example:
Input: "What is RAG?"
Output: ["How does RAG improve over traditional search?", "What are the limitations of RAG?", "How do I implement RAG in my application?"]
"""
        
        user_message = f"""
Original Question: {query}

Answer: {answer}

Generate {num_questions} relevant follow-up questions:
"""
        
        try:
            questions_text = await multi_model_service.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_message}
                ],
                model_name=multi_model_service.DEFAULT_LLM_MODEL,
                max_tokens=500
            )
            
            import json
            try:
                questions = json.loads(questions_text)
                if not isinstance(questions, list):
                    questions = [questions]
                return questions[:num_questions]
            except json.JSONDecodeError:
                # Fallback: extract question-like sentences
                questions = [
                    line.strip()
                    for line in questions_text.split('\n')
                    if line.strip() and line.strip().endswith('?')
                ]
                return questions[:num_questions]
                
        except Exception as e:
            print(f"Error generating follow-up questions: {e}")
            return []
    
    async def summarize_contexts(
        self,
        contexts: List[Dict],
        max_length: int = 500
    ) -> str:
        """
        Summarize multiple contexts into a concise overview.
        
        Args:
            contexts: List of contexts
            max_length: Maximum length of summary
            
        Returns:
            Concise summary
        """
        if not contexts:
            return "No context available."
        
        # Combine context texts
        combined_text = " ".join([
            ctx.get('chunk', {}).get('content', '')
            for ctx in contexts[:5]  # Limit to top 5 contexts
        ])
        
        system_prompt = f"""Summarize the following text into a concise overview of at most {max_length} characters.

Focus on:
- Main topics and themes
- Key information
- Important details

Keep the summary clear and informative.
"""
        
        try:
            summary = await multi_model_service.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": combined_text}
                ],
                model_name=multi_model_service.DEFAULT_LLM_MODEL,
                max_tokens=300
            )
            
            return summary
            
        except Exception as e:
            print(f"Error summarizing contexts: {e}")
            # Fallback: simple concatenation
            return combined_text[:max_length] + "..."
    
    def calculate_answer_quality(
        self,
        answer: str,
        contexts: List[Dict],
        citations: List[int]
    ) -> Dict[str, float]:
        """
        Calculate quality metrics for the generated answer.
        
        Args:
            answer: Generated answer
            contexts: Contexts used
            citations: Citations in answer
            
        Returns:
            Dictionary with quality metrics
        """
        # Citation coverage
        unique_citations = len(set(citations))
        citation_coverage = unique_citations / len(contexts) if contexts else 0
        
        # Citation density (citations per 100 words)
        word_count = len(answer.split())
        citation_density = (len(citations) / word_count * 100) if word_count > 0 else 0
        
        # Answer length
        answer_length = len(answer)
        
        # Context utilization (how many different contexts were cited)
        context_utilization = unique_citations / max(len(contexts), 1)
        
        return {
            'citation_coverage': citation_coverage,
            'citation_density': citation_density,
            'answer_length': answer_length,
            'word_count': word_count,
            'context_utilization': context_utilization,
            'unique_citations': unique_citations,
            'total_citations': len(citations)
        }


# Global advanced RAG service instance
advanced_rag_service = AdvancedRAGService()