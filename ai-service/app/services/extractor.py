import asyncio
from typing import Optional
from pathlib import Path
import aiofiles

# Document processing libraries
from pypdf import PdfReader
from docx import Document as DocxDocument
from bs4 import BeautifulSoup
import pandas as pd


class DocumentExtractor:
    """Service for extracting text from various document formats"""
    
    @staticmethod
    async def extract_text(file_path: str, mime_type: str) -> str:
        """
        Extract text from a document file based on MIME type
        
        Args:
            file_path: Path to the document file
            mime_type: MIME type of the file
            
        Returns:
            Extracted text content
        """
        mime_type = mime_type.lower()
        
        if mime_type == 'application/pdf':
            return await DocumentExtractor._extract_pdf(file_path)
        elif mime_type in [
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/msword'
        ]:
            return await DocumentExtractor._extract_docx(file_path)
        elif mime_type == 'text/plain':
            return await DocumentExtractor._extract_text_file(file_path)
        elif mime_type == 'text/html':
            return await DocumentExtractor._extract_html(file_path)
        elif mime_type == 'text/csv':
            return await DocumentExtractor._extract_csv(file_path)
        else:
            raise ValueError(f"Unsupported MIME type: {mime_type}")
    
    @staticmethod
    async def _extract_pdf(file_path: str) -> str:
        """Extract text from PDF file"""
        try:
            reader = PdfReader(file_path)
            text_parts = []
            
            for page_num, page in enumerate(reader.pages):
                try:
                    text = page.extract_text()
                    if text.strip():
                        # Add page number for reference
                        text_parts.append(f"[Page {page_num + 1}]\n{text}")
                except Exception as e:
                    print(f"Error extracting text from page {page_num}: {e}")
                    continue
            
            return '\n\n'.join(text_parts)
        except Exception as e:
            raise ValueError(f"Failed to extract text from PDF: {e}")
    
    @staticmethod
    async def _extract_docx(file_path: str) -> str:
        """Extract text from DOCX file"""
        try:
            doc = DocxDocument(file_path)
            text_parts = []
            
            for paragraph in doc.paragraphs:
                if paragraph.text.strip():
                    text_parts.append(paragraph.text)
            
            return '\n'.join(text_parts)
        except Exception as e:
            raise ValueError(f"Failed to extract text from DOCX: {e}")
    
    @staticmethod
    async def _extract_text_file(file_path: str) -> str:
        """Extract text from plain text file"""
        try:
            async with aiofiles.open(file_path, 'r', encoding='utf-8') as f:
                return await f.read()
        except UnicodeDecodeError:
            # Try with different encoding
            try:
                async with aiofiles.open(file_path, 'r', encoding='latin-1') as f:
                    return await f.read()
            except Exception as e:
                raise ValueError(f"Failed to read text file: {e}")
        except Exception as e:
            raise ValueError(f"Failed to extract text from TXT: {e}")
    
    @staticmethod
    async def _extract_html(file_path: str) -> str:
        """Extract text from HTML file"""
        try:
            async with aiofiles.open(file_path, 'r', encoding='utf-8') as f:
                html_content = await f.read()
            
            soup = BeautifulSoup(html_content, 'html.parser')
            
            # Remove script and style elements
            for script in soup(["script", "style"]):
                script.decompose()
            
            # Get text
            text = soup.get_text()
            
            # Break into lines and remove leading/trailing space
            lines = (line.strip() for line in text.splitlines())
            
            # Break multi-headlines into a line each
            chunks = (phrase.strip() for line in lines for phrase in line.split("  "))
            
            # Drop blank lines
            text = '\n'.join(chunk for chunk in chunks if chunk)
            
            return text
        except Exception as e:
            raise ValueError(f"Failed to extract text from HTML: {e}")
    
    @staticmethod
    async def _extract_csv(file_path: str) -> str:
        """Extract text from CSV file"""
        try:
            df = pd.read_csv(file_path)
            
            # Convert DataFrame to text representation
            text_parts = []
            
            # Add headers
            headers = ', '.join(str(col) for col in df.columns)
            text_parts.append(f"Headers: {headers}\n")
            
            # Add data rows
            for _, row in df.iterrows():
                row_text = ', '.join(str(val) for val in row)
                text_parts.append(row_text)
            
            return '\n'.join(text_parts)
        except Exception as e:
            raise ValueError(f"Failed to extract text from CSV: {e}")
    
    @staticmethod
    def get_supported_mime_types() -> list:
        """Return list of supported MIME types"""
        return [
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/msword',
            'text/plain',
            'text/html',
            'text/csv'
        ]