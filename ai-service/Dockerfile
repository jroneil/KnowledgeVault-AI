FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    libpoppler-cpp-dev \
    libxml2-dev \
    libxslt-dev \
    curl \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-fre \
    tesseract-ocr-deu \
    tesseract-ocr-spa \
    libtesseract-dev \
    libleptonica-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables for OCR
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata
ENV OCR_LANGUAGES=eng,fre,deu,spa
ENV OCR_DPI=300
ENV OCR_TIMEOUT=120

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create non-root user
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:8000/health')"

# Run the application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]