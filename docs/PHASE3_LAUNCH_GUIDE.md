# Phase 3: AI Service Foundation - Launch Guide

**Date:** June 22, 2026  
**Status:** Ready to Launch  
**Objective:** Deploy and test Phase 3 AI Service with external Ollama integration

---

## Overview

Phase 3 code is complete and ready for deployment. This guide walks through launching the AI service with the external Ollama instance at `192.168.1.24:11434`.

**What Phase 3 Delivers:**
- FastAPI AI service (port 8000)
- Document text extraction (PDF, DOCX, TXT, HTML, CSV)
- Intelligent document chunking with overlap
- Ollama integration for embeddings and LLM
- Internal API authentication
- Complete Docker deployment

---

## Prerequisites

### 1. External Ollama Service
- **Location:** 192.168.1.24:11434
- **Required Models:**
  - `nomic-embed-text` (for embeddings)
  - `qwen2:7b` (for LLM/chat)

Verify models are installed:
```bash
curl http://192.168.1.24:11434/api/tags
```

### 2. Docker Desktop
- Docker Desktop must be running
- Verify with: `docker ps`

### 3. Network Access
- Ensure Docker can reach 192.168.1.24:11434
- AI service container uses `extra_hosts` for host networking

---

## Launch Steps

### Step 1: Start Docker Desktop

**Windows:**
1. Open Docker Desktop
2. Wait for "Docker Desktop is running" message
3. Verify with: `docker ps`

### Step 2: Build and Start Services

```bash
# Navigate to project directory
cd e:\KnowledgeVault-AI

# Build and start all services
docker-compose up -d --build

# Check service status
docker-compose ps

# View AI service logs
docker-compose logs -f ai-service
```

**Expected Output:**
- All services should show "Up" status
- AI service should show "Ollama service is available"
- AI service should show available models

### Step 3: Verify Service Health

**1. Check AI Service Health:**
```bash
curl http://localhost:8000/health
```

Expected response:
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "timestamp": "2026-06-22T...",
  "services": {
    "ollama": true
  }
}
```

**2. Check Detailed Health:**
```bash
curl http://localhost:8000/health/detailed
```

**3. View API Documentation:**
```
Open browser: http://localhost:8000/docs
```

### Step 4: Test Ollama Connection

**From AI Service Container:**
```bash
# Enter AI service container
docker exec -it knowledgevault-ai bash

# Test Ollama connection
curl http://192.168.1.24:11434/api/tags

# Test embedding generation
curl -X POST http://192.168.1.24:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "nomic-embed-text",
    "prompt": "Hello, World!"
  }'

# Exit container
exit
```

### Step 5: Test AI Service Endpoints

**1. Test Text Extraction:**
```bash
curl -X POST http://localhost:8000/api/v1/processing/extract \
  -H "Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe" \
  -H "Content-Type: application/json" \
  -d '{
    "file_path": "/app/storage/documents/test.pdf",
    "mime_type": "application/pdf"
  }'
```

**2. Test Document Chunking:**
```bash
curl -X POST http://localhost:8000/api/v1/processing/chunk \
  -H "Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "This is a sample document. It contains multiple sentences. We want to test the chunking functionality.",
    "chunk_size": 50,
    "overlap": 10
  }'
```

**3. Test Embedding Generation:**
```bash
curl -X POST http://localhost:8000/api/v1/processing/embed \
  -H "Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Test embedding generation",
    "model": "nomic-embed-text"
  }'
```

### Step 6: End-to-End Ingestion Test

**1. Upload a Document via Backend:**
```bash
# First, login to get JWT token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'

# Save the token from response
```

**2. Trigger AI Ingestion:**
```bash
curl -X POST http://localhost:8000/api/v1/processing/ingest \
  -H "Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe" \
  -H "Content-Type: application/json" \
  -d '{
    "document_id": 1,
    "version_id": 1,
    "file_path": "/app/storage/documents/1/v1.pdf",
    "mime_type": "application/pdf"
  }'
```

**3. Check Ingestion Status:**
```bash
# Use job_id from previous response
curl http://localhost:8000/api/v1/processing/ingest/{job_id} \
  -H "Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe"
```

### Step 7: Verify Database Integration

**Check if chunks and embeddings tables exist:**
```bash
# Connect to PostgreSQL
docker exec -it knowledgevault-db psql -U postgres -d knowledgevault

# Check tables
\dt document_chunks
\dt embeddings

# Sample query
SELECT COUNT(*) FROM document_chunks;
SELECT COUNT(*) FROM embeddings;

# Exit
exit
```

---

## Troubleshooting

### Issue 1: AI Service Won't Start

**Symptoms:**
- Container shows "Restarting" status
- Logs show connection errors

**Solutions:**
```bash
# Check logs
docker-compose logs ai-service

# Common issues:
# 1. Ollama not accessible at 192.168.1.24:11434
# 2. Database connection issues
# 3. Missing Python dependencies
```

### Issue 2: Ollama Connection Refused

**Symptoms:**
- AI service logs show "Ollama service is not available"
- Health check shows `services.ollama: false`

**Solutions:**
```bash
# Test connectivity from AI container
docker exec -it knowledgevault-ai bash
curl http://192.168.1.24:11434/api/tags

# If fails:
# 1. Verify Ollama is running on host
# 2. Check firewall rules
# 3. Verify IP address is correct
```

### Issue 3: Models Not Available

**Symptoms:**
- API returns "model not found" errors
- Embedding generation fails

**Solutions:**
```bash
# On the Ollama host (192.168.1.24):
ssh user@192.168.1.24

# Pull required models
ollama pull nomic-embed-text
ollama pull qwen2:7b

# Verify
ollama list
```

### Issue 4: File Access Denied

**Symptoms:**
- Text extraction fails with file access errors
- "File not found" or "Permission denied"

**Solutions:**
```bash
# Check file exists in storage
docker exec -it knowledgevault-ai ls -la /app/storage/documents/

# Check permissions
docker exec -it knowledgevault-ai stat /app/storage/documents/1/v1.pdf

# If missing, upload via backend first
```

---

## Performance Verification

### Expected Performance Metrics

| Operation | Expected Time | Notes |
|-----------|---------------|-------|
| AI Service Startup | 10-30s | Including Ollama connection |
| Health Check | <1s | Simple status check |
| Text Extraction (PDF) | 1-5s | Depends on file size |
| Document Chunking | <1s | CPU-bound operation |
| Embedding Generation | 2-10s per chunk | Depends on model |
| Full Ingestion (50KB PDF) | 1-3 min | Including all steps |

### Performance Test Script

```bash
# Test embedding generation speed
time curl -X POST http://localhost:8000/api/v1/processing/embed \
  -H "Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "This is a performance test for embedding generation speed.",
    "model": "nomic-embed-text"
  }'
```

---

## API Endpoint Summary

### Health Endpoints
```
GET  /health              # Basic health check
GET  /health/detailed     # Detailed health with service status
GET  /docs                # Swagger documentation
GET  /openapi.json        # OpenAPI schema
```

### Processing Endpoints (Authentication Required)
```
POST /api/v1/processing/ingest               # Start document ingestion
GET  /api/v1/processing/ingest/{job_id}      # Get ingestion status
POST /api/v1/processing/extract              # Extract text from document
POST /api/v1/processing/chunk                # Chunk document text
POST /api/v1/processing/embed                # Generate embeddings
```

### Authentication
All processing endpoints require `Authorization: Bearer 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe` header.

---

## Service Architecture

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   Next.js 16     │────▶│  Spring Boot     │────▶│   FastAPI       │
│   Frontend       │ REST│  Document Service│ REST│  AI Service     │
│   (port 3000)    │◀────│   (port 8080)    │◀────│   (port 8000)   │
└──────────────────┘     └────────┬─────────┘     └────────┬─────────┘
                                    │                        │
                                    ▼                        ▼
                           ┌──────────────────────────────────┐
                           │   PostgreSQL 16 + pgvector       │
                           │      (port 5432)                 │
                           └──────────────────────────────────┘
                                                          ▲
                                                    ┌─────┴─────┐
                                                    │   Ollama   │
                                                    │192.168.1.24│
                                                    │  (11434)   │
                                                    └────────────┘
```

---

## Configuration Files

### AI Service Environment Variables
```yaml
DATABASE_URL: postgresql+asyncpg://postgres:postgres@postgres:5432/knowledgevault
OLLAMA_BASE_URL: http://192.168.1.24:11434
INTERNAL_API_KEY: 9XfK2mQ7vLp8Rz4NcT1wHy6BjDs3UaEe
CHUNK_SIZE: 1000
CHUNK_OVERLAP: 200
MAX_FILE_SIZE: 52428800  # 50MB
```

### Ollama Models
```bash
# Required models:
nomic-embed-text  # 768-dimensional embeddings
qwen2:7b          # 7B parameter LLM
```

---

## Next Steps After Phase 3 Launch

1. **Test with Real Documents** - Upload actual PDF/DOCX files
2. **Monitor Performance** - Check embedding generation times
3. **Verify Database Storage** - Confirm chunks and embeddings are stored
4. **Prepare for Phase 4** - Plan vector search implementation
5. **Documentation** - Update user guides with AI capabilities

---

## Phase 3 Completion Checklist

- [ ] Docker Desktop is running
- [ ] All services start successfully (`docker-compose ps`)
- [ ] AI service health check passes (`/health`)
- [ ] Ollama connection verified (logs show "Ollama service is available")
- [ ] Text extraction tested with sample document
- [ ] Document chunking tested successfully
- [ ] Embedding generation tested with nomic-embed-text
- [ ] End-to-end ingestion tested with uploaded document
- [ ] Database tables verified (chunks and embeddings)
- [ ] API documentation accessible (`/docs`)
- [ ] Performance metrics within expected ranges
- [ ] No critical errors in logs

---

## Support

**Log Locations:**
- AI Service: `docker-compose logs -f ai-service`
- Backend: `docker-compose logs -f backend`
- PostgreSQL: `docker-compose logs -f postgres`

**Useful Commands:**
```bash
# Restart AI service
docker-compose restart ai-service

# Rebuild AI service
docker-compose up -d --build ai-service

# View all logs
docker-compose logs -f

# Stop all services
docker-compose down

# Clean restart (remove volumes)
docker-compose down -v
docker-compose up -d --build
```

---

## Status

**Phase 3 Status:** ✅ Code Complete, Ready for Launch  
**Last Updated:** June 22, 2026  
**Next Phase:** Phase 4 - Vector Storage & Embeddings

---

**Launch Phase 3 by completing all steps in this guide. Once all checklist items are verified, Phase 3 is officially complete!**