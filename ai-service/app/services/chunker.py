from typing import List, Optional
from dataclasses import dataclass
from app.core.config import settings
import re


@dataclass
class DocumentChunk:
    """Represents a chunk of text from a document"""
    chunk_index: int
    content: str
    page_number: Optional[int] = None
    section_name: Optional[str] = None
    token_count: int = 0


class DocumentChunker:
    """Service for chunking documents into smaller pieces"""
    
    def __init__(self, chunk_size: int = None, overlap: int = None):
        self.chunk_size = chunk_size or settings.CHUNK_SIZE
        self.overlap = overlap or settings.CHUNK_OVERLAP
    
    async def chunk_document(
        self,
        text: str,
        chunk_size: Optional[int] = None,
        overlap: Optional[int] = None
    ) -> List[DocumentChunk]:
        """
        Split document text into chunks with overlap
        
        Args:
            text: Full document text
            chunk_size: Size of each chunk (default from settings)
            overlap: Overlap between chunks (default from settings)
            
        Returns:
            List of DocumentChunk objects
        """
        chunk_size = chunk_size or self.chunk_size
        overlap = overlap or self.overlap
        
        chunks = []
        current_position = 0
        chunk_index = 0
        
        while current_position < len(text):
            # Calculate end position for this chunk
            end_position = min(current_position + chunk_size, len(text))
            
            # Extract chunk content
            chunk_content = text[current_position:end_position]
            
            # Try to find a good break point (period, question mark, exclamation)
            if end_position < len(text):
                last_break = self._find_last_break_point(chunk_content)
                if last_break > 0:
                    end_position = current_position + last_break + 1
                    chunk_content = text[current_position:end_position]
            
            # Extract metadata (page numbers, sections)
            metadata = self._extract_metadata(chunk_content)
            
            # Clean up content
            clean_content = self._clean_content(chunk_content)
            
            # Create chunk
            chunk = DocumentChunk(
                chunk_index=chunk_index,
                content=clean_content.strip(),
                page_number=metadata.get('page_number'),
                section_name=metadata.get('section_name'),
                token_count=self._estimate_tokens(clean_content)
            )
            
            chunks.append(chunk)
            chunk_index += 1
            
            # Move to next position with overlap
            current_position = end_position - overlap
            
            # Ensure we don't get stuck
            if current_position <= 0:
                current_position = end_position
            elif current_position >= len(text):
                break
        
        return chunks
    
    def _find_last_break_point(self, text: str) -> int:
        """
        Find the last sentence break point in the text
        
        Args:
            text: Text to search for break points
            
        Returns:
            Position of last break point, or -1 if not found
        """
        # Look for sentence endings in reverse order
        break_chars = ['.', '!', '?', '\n']
        
        for i in range(len(text) - 1, -1, -1):
            if text[i] in break_chars:
                # Ensure it's not part of an abbreviation
                if i < len(text) - 1 and text[i + 1].isspace():
                    return i
                elif text[i] == '\n':
                    return i
        
        return -1
    
    def _extract_metadata(self, chunk: str) -> dict:
        """
        Extract metadata from chunk (page numbers, sections)
        
        Args:
            chunk: Chunk content
            
        Returns:
            Dictionary with metadata
        """
        metadata = {}
        
        # Extract page numbers (format: [Page 123])
        page_match = re.search(r'\[Page\s+(\d+)\]', chunk)
        if page_match:
            metadata['page_number'] = int(page_match.group(1))
        
        # Extract section headers (format: ## Header or Header: with colon)
        section_match = re.search(r'^#+\s+(.+)$|^[A-Z][^.:]+:$', chunk, re.MULTILINE)
        if section_match:
            section_text = section_match.group(1) if section_match.group(1) else section_match.group(0)
            metadata['section_name'] = section_text.rstrip(':')
        
        return metadata
    
    def _clean_content(self, content: str) -> str:
        """
        Clean up chunk content
        
        Args:
            content: Raw chunk content
            
        Returns:
            Cleaned content
        """
        # Remove excessive whitespace
        content = re.sub(r'\s+', ' ', content)
        
        # Remove page markers (keep the info but clean format)
        content = re.sub(r'\[Page\s+\d+\]\s*', '', content)
        
        # Clean up other artifacts
        content = content.strip()
        
        return content
    
    def _estimate_tokens(self, text: str) -> int:
        """
        Estimate token count for text (rough approximation)
        
        Args:
            text: Text to estimate tokens for
            
        Returns:
            Estimated token count
        """
        # Rough approximation: ~4 characters per token for English
        return len(text) // 4
    
    async def chunk_with_sentence_boundaries(
        self,
        text: str,
        target_size: int = 1000,
        max_size: int = 1500
    ) -> List[DocumentChunk]:
        """
        Chunk text respecting sentence boundaries
        
        Args:
            text: Text to chunk
            target_size: Target chunk size
            max_size: Maximum chunk size before forcing split
            
        Returns:
            List of DocumentChunk objects
        """
        # Split into sentences
        sentences = re.split(r'(?<=[.!?])\s+', text)
        
        chunks = []
        current_chunk = []
        current_size = 0
        chunk_index = 0
        
        for sentence in sentences:
            sentence_size = self._estimate_tokens(sentence)
            
            if current_size + sentence_size <= target_size:
                current_chunk.append(sentence)
                current_size += sentence_size
            else:
                # Save current chunk if it exists
                if current_chunk:
                    chunk_content = ' '.join(current_chunk)
                    chunks.append(DocumentChunk(
                        chunk_index=chunk_index,
                        content=chunk_content,
                        token_count=current_size
                    ))
                    chunk_index += 1
                
                # Start new chunk
                if sentence_size > max_size:
                    # Sentence is too long, force split
                    chunks.append(DocumentChunk(
                        chunk_index=chunk_index,
                        content=sentence,
                        token_count=sentence_size
                    ))
                    chunk_index += 1
                    current_chunk = []
                    current_size = 0
                else:
                    current_chunk = [sentence]
                    current_size = sentence_size
        
        # Add final chunk
        if current_chunk:
            chunk_content = ' '.join(current_chunk)
            chunks.append(DocumentChunk(
                chunk_index=chunk_index,
                content=chunk_content,
                token_count=current_size
            ))
        
        return chunks