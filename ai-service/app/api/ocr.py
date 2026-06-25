"""
OCR API endpoints for processing scanned documents.
"""

from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

from app.core.config import settings
from app.services.ocr_service import ocr_service

# Security
security = HTTPBearer()

# Router
router = APIRouter()


# Request/Response Models

class OCRProcessRequest(BaseModel):
    """Request for OCR processing."""
    file_path: str = Field(..., description="Path to PDF or image file")
    output_format: str = Field("text", description="Output format: text, hocr, or xml")
    languages: Optional[List[str]] = Field(None, description="Language codes (e.g., ['eng', 'fra'])")


class OCRProcessResponse(BaseModel):
    """Response from OCR processing."""
    text: str = Field(..., description="Extracted text")
    confidence: float = Field(..., description="Average confidence score (0.0-1.0)")
    pages_processed: int = Field(..., description="Number of pages processed")
    processing_time_ms: float = Field(..., description="Processing time in milliseconds")
    language_detected: str = Field(..., description="Primary language detected")
    page_details: Optional[List[dict]] = Field(None, description="Details for each page")


class DetectMixedRequest(BaseModel):
    """Request for mixed content detection."""
    file_path: str = Field(..., description="Path to PDF file")


class DetectMixedResponse(BaseModel):
    """Response from mixed content detection."""
    total_pages: int = Field(..., description="Total number of pages")
    text_pages: List[int] = Field(..., description="Page numbers with extractable text")
    scanned_pages: List[int] = Field(..., description="Page numbers that need OCR")
    needs_ocr: bool = Field(..., description="Whether OCR is needed")


class SmartExtractRequest(BaseModel):
    """Request for smart text extraction."""
    file_path: str = Field(..., description="Path to document file")
    enable_ocr: bool = Field(True, description="Whether to use OCR for scanned content")


class SmartExtractResponse(BaseModel):
    """Response from smart text extraction."""
    text: str = Field(..., description="Extracted text")
    method: str = Field(..., description="Method used: standard or ocr")
    pages_processed: int = Field(..., description="Number of pages processed")
    processing_time_ms: float = Field(..., description="Processing time in milliseconds")
    ocr_used: bool = Field(..., description="Whether OCR was used")
    confidence: Optional[float] = Field(None, description="OCR confidence score")
    warning: Optional[str] = Field(None, description="Warning message")


class LanguageInfo(BaseModel):
    """Information about a supported language."""
    code: str = Field(..., description="Language code")
    name: str = Field(..., description="Language name")


class LanguagesResponse(BaseModel):
    """Response with supported languages."""
    languages: List[LanguageInfo] = Field(..., description="List of supported languages")


# Helper Functions

async def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """Verify internal API token."""
    if credentials.credentials != settings.INTERNAL_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token"
        )
    return credentials.credentials


# API Endpoints

@router.post("/process", response_model=OCRProcessResponse)
async def process_document(
    request: OCRProcessRequest,
    token: str = Depends(verify_token)
):
    """
    Process scanned document with OCR.
    
    Supports PDF and image files (PNG, JPG, JPEG, TIFF, BMP, GIF).
    Extracts text with confidence scores and page-level details.
    """
    try:
        # Validate languages
        if request.languages:
            for lang in request.languages:
                if not ocr_service.is_language_supported(lang):
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST,
                        detail=f"Unsupported language code: {lang}"
                    )
        
        # Process document with OCR
        result = await ocr_service.process_scanned_document(
            file_path=request.file_path,
            output_format=request.output_format,
            languages=request.languages
        )
        
        return OCRProcessResponse(
            text=result.text,
            confidence=result.confidence,
            pages_processed=result.pages_processed,
            processing_time_ms=result.processing_time_ms,
            language_detected=result.language_detected,
            page_details=result.page_details
        )
    
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"OCR processing failed: {str(e)}"
        )


@router.post("/detect-mixed", response_model=DetectMixedResponse)
async def detect_mixed_content(
    request: DetectMixedRequest,
    token: str = Depends(verify_token)
):
    """
    Detect mixed content in PDF (text vs. scanned pages).
    
    Analyzes each page to determine if it has extractable text
    or requires OCR processing.
    """
    try:
        result = await ocr_service.process_pdf_with_mixed_content(request.file_path)
        
        return DetectMixedResponse(
            total_pages=result.total_pages,
            text_pages=result.text_pages,
            scanned_pages=result.scanned_pages,
            needs_ocr=result.needs_ocr
        )
    
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Mixed content detection failed: {str(e)}"
        )


@router.post("/extract-smart", response_model=SmartExtractResponse)
async def smart_extract_text(
    request: SmartExtractRequest,
    token: str = Depends(verify_token)
):
    """
    Smart text extraction with automatic OCR detection.
    
    Automatically detects if a PDF has scanned content and applies
    OCR as needed. Falls back to standard text extraction for
    text-based documents.
    """
    try:
        result = await ocr_service.extract_text_smart(
            file_path=request.file_path,
            enable_ocr=request.enable_ocr
        )
        
        return SmartExtractResponse(**result)
    
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Smart extraction failed: {str(e)}"
        )


@router.get("/languages", response_model=LanguagesResponse)
async def get_supported_languages(token: str = Depends(verify_token)):
    """
    Get list of supported OCR languages.
    
    Returns all available language codes and names for OCR processing.
    """
    try:
        languages = ocr_service.get_supported_languages()
        
        return LanguagesResponse(
            languages=[
                LanguageInfo(code=lang['code'], name=lang['name'])
                for lang in languages
            ]
        )
    
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get languages: {str(e)}"
        )


@router.get("/health")
async def ocr_health_check(token: str = Depends(verify_token)):
    """
    Check OCR service health.
    
    Verifies Tesseract OCR is installed and accessible.
    """
    try:
        import pytesseract
        
        # Check Tesseract version
        version = pytesseract.get_tesseract_version()
        
        # Check tessdata directory
        tessdata_exists = ocr_service.tessdata_prefix and os.path.exists(ocr_service.tessdata_prefix)
        
        # Get supported languages
        languages = ocr_service.get_supported_languages()
        
        return {
            "status": "healthy",
            "tesseract_version": str(version),
            "tessdata_prefix": ocr_service.tessdata_prefix,
            "tessdata_exists": tessdata_exists,
            "supported_languages": len(languages),
            "default_dpi": ocr_service.dpi,
            "timeout_seconds": ocr_service.timeout
        }
    
    except Exception as e:
        return {
            "status": "unhealthy",
            "error": str(e)
        }
