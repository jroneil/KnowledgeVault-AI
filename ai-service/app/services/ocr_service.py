"""
OCR service for processing scanned documents and images.
Supports PDF to image conversion and text extraction using Tesseract OCR.
"""

import os
import tempfile
import hashlib
from typing import List, Dict, Optional, Tuple
from PIL import Image
import pytesseract
from pdf2image import convert_from_path
from pypdf import PdfReader
import logging

from app.core.config import settings

logger = logging.getLogger(__name__)


class OCRResult:
    """Result of OCR processing."""
    
    def __init__(
        self,
        text: str,
        confidence: float,
        pages_processed: int,
        processing_time_ms: float,
        language_detected: str,
        page_details: Optional[List[Dict]] = None
    ):
        self.text = text
        self.confidence = confidence
        self.pages_processed = pages_processed
        self.processing_time_ms = processing_time_ms
        self.language_detected = language_detected
        self.page_details = page_details or []
    
    def to_dict(self) -> Dict:
        """Convert to dictionary."""
        return {
            'text': self.text,
            'confidence': self.confidence,
            'pages_processed': self.pages_processed,
            'processing_time_ms': self.processing_time_ms,
            'language_detected': self.language_detected,
            'page_details': self.page_details
        }


class MixedContentResult:
    """Result of mixed content detection."""
    
    def __init__(
        self,
        total_pages: int,
        text_pages: List[int],
        scanned_pages: List[int],
        needs_ocr: bool
    ):
        self.total_pages = total_pages
        self.text_pages = text_pages
        self.scanned_pages = scanned_pages
        self.needs_ocr = needs_ocr
    
    def to_dict(self) -> Dict:
        """Convert to dictionary."""
        return {
            'total_pages': self.total_pages,
            'text_pages': self.text_pages,
            'scanned_pages': self.scanned_pages,
            'needs_ocr': self.needs_ocr
        }


class LanguageInfo:
    """Information about a supported OCR language."""
    
    def __init__(self, code: str, name: str):
        self.code = code
        self.name = name
    
    def to_dict(self) -> Dict:
        """Convert to dictionary."""
        return {
            'code': self.code,
            'name': self.name
        }


class OCRService:
    """
    OCR service for processing scanned documents.
    """
    
    # Supported languages
    SUPPORTED_LANGUAGES = {
        'eng': 'English',
        'fra': 'French',
        'deu': 'German',
        'spa': 'Spanish',
        'ita': 'Italian',
        'por': 'Portuguese',
        'rus': 'Russian',
        'jpn': 'Japanese',
        'kor': 'Korean',
        'chi_sim': 'Chinese (Simplified)',
        'chi_tra': 'Chinese (Traditional)'
    }
    
    def __init__(self):
        """Initialize OCR service."""
        self.tessdata_prefix = os.environ.get('TESSDATA_PREFIX', '/usr/share/tesseract-ocr/4.00/tessdata')
        self.dpi = int(os.environ.get('OCR_DPI', '300'))
        self.timeout = int(os.environ.get('OCR_TIMEOUT', '120'))
        
        # Set tesseract path if specified
        tesseract_path = os.environ.get('TESSERACT_PATH')
        if tesseract_path:
            pytesseract.pytesseract.tesseract_cmd = tesseract_path
    
    async def process_scanned_document(
        self,
        file_path: str,
        output_format: str = "text",
        languages: Optional[List[str]] = None
    ) -> OCRResult:
        """
        Process scanned document with OCR.
        
        Args:
            file_path: Path to PDF or image file
            output_format: "text", "hocr", or "xml"
            languages: List of language codes (e.g., ["eng", "fra"])
        
        Returns:
            OCRResult with extracted text, confidence scores, and layout
        
        Raises:
            FileNotFoundError: If file doesn't exist
            ValueError: If file format is not supported
        """
        import time
        start_time = time.time()
        
        # Validate file exists
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")
        
        # Set languages (default to English)
        languages = languages or ['eng']
        lang_string = '+'.join(languages)
        
        logger.info(f"Starting OCR processing: {file_path}, languages: {lang_string}")
        
        try:
            # Determine file type and process accordingly
            file_ext = os.path.splitext(file_path)[1].lower()
            
            if file_ext == '.pdf':
                result = await self._process_pdf(file_path, lang_string, output_format)
            elif file_ext in ['.png', '.jpg', '.jpeg', '.tiff', '.bmp', '.gif']:
                result = await self._process_image(file_path, lang_string, output_format)
            else:
                raise ValueError(f"Unsupported file format: {file_ext}")
            
            processing_time = (time.time() - start_time) * 1000
            result.processing_time_ms = processing_time
            
            logger.info(f"OCR processing completed: {result.pages_processed} pages in {processing_time:.0f}ms")
            return result
            
        except Exception as e:
            logger.error(f"OCR processing failed: {str(e)}")
            raise
    
    async def _process_pdf(
        self,
        file_path: str,
        lang_string: str,
        output_format: str
    ) -> OCRResult:
        """Process PDF file with OCR."""
        try:
            # Convert PDF to images
            logger.info(f"Converting PDF to images at {self.dpi} DPI")
            images = convert_from_path(
                file_path,
                dpi=self.dpi,
                fmt='jpeg'
            )
            
            all_text = []
            page_details = []
            total_confidence = 0.0
            
            # Process each page
            for page_num, image in enumerate(images, 1):
                page_start = time.time()
                
                # Perform OCR on page
                page_text, page_confidence = self._perform_ocr(image, lang_string, output_format)
                
                all_text.append(page_text)
                total_confidence += page_confidence
                
                page_details.append({
                    'page_number': page_num,
                    'text_length': len(page_text),
                    'confidence': page_confidence,
                    'processing_time_ms': (time.time() - page_start) * 1000
                })
                
                logger.debug(f"Page {page_num} processed: {len(page_text)} chars, confidence: {page_confidence:.2f}")
            
            # Calculate average confidence
            avg_confidence = total_confidence / len(images) if images else 0.0
            
            return OCRResult(
                text='\n\n'.join(all_text),
                confidence=avg_confidence,
                pages_processed=len(images),
                processing_time_ms=0.0,  # Will be set by caller
                language_detected=lang_string.split('+')[0],
                page_details=page_details
            )
            
        except Exception as e:
            logger.error(f"PDF OCR processing failed: {str(e)}")
            raise
    
    async def _process_image(
        self,
        file_path: str,
        lang_string: str,
        output_format: str
    ) -> OCRResult:
        """Process image file with OCR."""
        try:
            # Open image
            image = Image.open(file_path)
            
            # Perform OCR
            text, confidence = self._perform_ocr(image, lang_string, output_format)
            
            return OCRResult(
                text=text,
                confidence=confidence,
                pages_processed=1,
                processing_time_ms=0.0,  # Will be set by caller
                language_detected=lang_string.split('+')[0],
                page_details=[{
                    'page_number': 1,
                    'text_length': len(text),
                    'confidence': confidence
                }]
            )
            
        except Exception as e:
            logger.error(f"Image OCR processing failed: {str(e)}")
            raise
    
    def _perform_ocr(
        self,
        image: Image.Image,
        lang_string: str,
        output_format: str
    ) -> Tuple[str, float]:
        """
        Perform OCR on a single image.
        
        Returns:
            Tuple of (text, confidence)
        """
        # Configure OCR options
        config = f'--tessdata-dir {self.tessdata_prefix}'
        
        try:
            # Get text and data
            if output_format == 'text':
                data = pytesseract.image_to_data(
                    image,
                    lang=lang_string,
                    config=config,
                    output_type=pytesseract.Output.DICT
                )
                
                # Extract text
                text = ' '.join([word for word in data['text'] if word.strip()])
                
                # Calculate confidence (average of all words)
                confidences = [int(conf) for conf in data['conf'] if conf != '-1']
                avg_confidence = sum(confidences) / len(confidences) / 100.0 if confidences else 0.0
                
            else:
                # Use default OCR
                text = pytesseract.image_to_string(
                    image,
                    lang=lang_string,
                    config=config
                )
                avg_confidence = 0.75  # Default confidence for non-OCR data formats
            
            return text, avg_confidence
            
        except Exception as e:
            logger.error(f"OCR failed on image: {str(e)}")
            return "", 0.0
    
    async def process_pdf_with_mixed_content(
        self,
        file_path: str
    ) -> MixedContentResult:
        """
        Detect and process PDFs with both text and scanned content.
        
        Args:
            file_path: Path to PDF file
        
        Returns:
            MixedContentResult indicating text vs. scanned pages
        """
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")
        
        try:
            # Read PDF
            pdf_reader = PdfReader(file_path)
            total_pages = len(pdf_reader.pages)
            
            text_pages = []
            scanned_pages = []
            
            # Check each page
            for page_num, page in enumerate(pdf_reader.pages, 1):
                text = page.extract_text()
                
                # If page has substantial text, consider it a text page
                # (scanned pages typically have little or no extractable text)
                if text and len(text.strip()) > 50:
                    text_pages.append(page_num)
                else:
                    scanned_pages.append(page_num)
            
            needs_ocr = len(scanned_pages) > 0
            
            result = MixedContentResult(
                total_pages=total_pages,
                text_pages=text_pages,
                scanned_pages=scanned_pages,
                needs_ocr=needs_ocr
            )
            
            logger.info(f"Mixed content detection: {total_pages} pages, {len(text_pages)} text, {len(scanned_pages)} scanned")
            return result
            
        except Exception as e:
            logger.error(f"Mixed content detection failed: {str(e)}")
            raise
    
    async def extract_text_smart(
        self,
        file_path: str,
        enable_ocr: bool = True
    ) -> Dict:
        """
        Smart text extraction with automatic OCR detection.
        
        Args:
            file_path: Path to document file
            enable_ocr: Whether to use OCR for scanned content
        
        Returns:
            Dictionary with extracted text and method used
        """
        import time
        start_time = time.time()
        
        try:
            # Try standard text extraction first
            from app.services.extractor import DocumentExtractor
            extractor = DocumentExtractor()
            
            file_ext = os.path.splitext(file_path)[1].lower()
            
            if file_ext == '.pdf':
                # Check for mixed content
                mixed_result = await self.process_pdf_with_mixed_content(file_path)
                
                if not mixed_result.needs_ocr:
                    # All text pages, use standard extraction
                    text = await extractor.extract_text_from_pdf(file_path)
                    return {
                        'text': text,
                        'method': 'standard',
                        'pages_processed': mixed_result.total_pages,
                        'processing_time_ms': (time.time() - start_time) * 1000,
                        'ocr_used': False
                    }
                
                if enable_ocr:
                    # Has scanned pages, use OCR
                    ocr_result = await self.process_scanned_document(file_path)
                    return {
                        'text': ocr_result.text,
                        'method': 'ocr',
                        'pages_processed': ocr_result.pages_processed,
                        'processing_time_ms': ocr_result.processing_time_ms,
                        'ocr_used': True,
                        'confidence': ocr_result.confidence
                    }
                else:
                    # OCR disabled, extract only text pages
                    return {
                        'text': '',
                        'method': 'standard',
                        'pages_processed': 0,
                        'processing_time_ms': (time.time() - start_time) * 1000,
                        'ocr_used': False,
                        'warning': 'OCR disabled for scanned pages'
                    }
            else:
                # Non-PDF file, use standard extraction
                text = await extractor.extract_text(file_path, 'application/octet-stream')
                return {
                    'text': text,
                    'method': 'standard',
                    'pages_processed': 1,
                    'processing_time_ms': (time.time() - start_time) * 1000,
                    'ocr_used': False
                }
                
        except Exception as e:
            logger.error(f"Smart text extraction failed: {str(e)}")
            raise
    
    def get_supported_languages(self) -> List[Dict]:
        """
        Get list of supported OCR languages.
        
        Returns:
            List of LanguageInfo dictionaries
        """
        return [
            LanguageInfo(code, name).to_dict()
            for code, name in self.SUPPORTED_LANGUAGES.items()
        ]
    
    def is_language_supported(self, language_code: str) -> bool:
        """
        Check if a language is supported.
        
        Args:
            language_code: Language code to check
        
        Returns:
            True if language is supported
        """
        return language_code in self.SUPPORTED_LANGUAGES


# Global OCR service instance
ocr_service = OCRService()