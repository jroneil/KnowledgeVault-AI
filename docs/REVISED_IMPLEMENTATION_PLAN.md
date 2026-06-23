# KnowledgeVault AI — Revised Implementation Plan

**Version:** 2.0  
**Date:** June 18, 2026  
**Status:** Updated for Document Storage Module  
**Author:** Engineering Team

> **What's Changed:** This revised plan incorporates the new Document Storage Module PRD while maintaining the original AI/RAG vision for future phases. Phase 1 (Backend Core) is complete.

---

## Executive Summary

This revised implementation plan reflects the addition of a comprehensive Document Storage Module that provides complete document management capabilities independent of AI features. The plan builds upon the completed Phase 1 (Backend Core) and introduces a more structured approach to separating document management from AI capabilities.

**Key Changes from Original Plan:**
- Added dedicated Document Storage Module phase (Phase 2)
- Clearer separation between document management and AI processing
- More detailed file storage architecture
- Enhanced metadata management capabilities
- Maintains all original AI/RAG capabilities for later phases

---

## Current Status

### ✅ Phase 1: Backend Core (COMPLETED)

**Completed Deliverables:**
- Spring Boot 4.1.0 backend application
- JWT-based authentication system
- User management with CRUD operations
- Role-based authorization (ADMIN/CONTRIBUTOR/VIEWER)
- Audit logging infrastructure
- PostgreSQL 16 database with pgvector extension
- Flyway migrations framework
- Docker Compose deployment setup
- Health check endpoints
- API testing infrastructure

**Technology Stack Verified:**
- Java 17, Spring Boot 4.1.0, Spring Security
- PostgreSQL 16 + pgvector
- Maven build system
- Docker & Docker Compose
- JWT authentication with jjwt library

**Database Migrations Completed:**
- ✅ V001__create_users_roles.sql (users, roles, user_roles tables)
- ✅ Initial admin user seeding
- ✅ Security configuration and testing

---

## Architecture Overview (Updated)

```
┌──────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Next.js 16     │────▶│  Spring Boot     │────▶│   FastAPI       │
│   Frontend       │ REST│  Document Service│ REST│  AI Service     │
│   (port 3000)    │◀────│   (port 8080)    │◀────│   (port 8000)   │
└──────────────────┘     └────────┬─────────┘     └────────┬─────────┘
                                  │ JDBC                   │ asyncpg
                                  ▼                        ▼
                         ┌──────────────────────────────────┐
                         │   PostgreSQL 16 + pgvector       │
                         │      (port 5432)                 │
                         └──────────────────────────────────┘
                                  ▲
                         ┌────────┴─────────┐
                         │   File Storage   │
                         │  documents/      │
                         │  originals/      │
                         │  processed/      │
                         └──────────────────┘
```

**Responsibility Boundaries (Updated):**

| Layer | Phase 2 Responsibilities | Future AI Responsibilities |
|-------|-------------------------|---------------------------|
| **Spring Boot** | Auth, users, roles, document metadata, file storage, collections, versions, audit, licensing, branding | AI service proxy, search coordination |
| **File Storage** | Original document storage, version management, file organization | Processed file storage, chunk storage |
| **PostgreSQL** | Business data, document metadata, audit logs | Vector embeddings, document chunks |
| **FastAPI** | (Not implemented in Phase 2) | Document processing, OCR, embeddings, semantic search, RAG |
| **Next.js** | Document UI, auth, file upload, viewing | AI search interface, chat UI |

---

## Implementation Phases (Revised)

### Phase 1: Backend Core ✅ COMPLETED

**Status:** Fully implemented and tested  
**Timeline:** Weeks 1-2 (Completed)

**Delivered:**
- Spring Security with JWT authentication
- User management (CRUD operations)
- Role-based authorization system
- Audit logging framework
- Database initialization with Flyway
- Docker Compose orchestration
- Health check endpoints

**Database Tables:**
- ✅ `users` (id, username, email, password_hash, status, created_at, updated_at)
- ✅ `roles` (id, name, description) - ADMIN, CONTRIBUTOR, VIEWER
- ✅ `user_roles` (user_id, role_id)
- ✅ `audit_log` (id, user_id, action, entity_type, entity_id, details, created_at)

---

### Phase 2: Document Storage Module 🚧 NEXT PHASE

**Timeline:** Weeks 3-5  
**Objective:** Complete document management system independent of AI features

**Core Capabilities:**
- Document upload and storage
- Collection organization
- Version management
- Metadata management
- Document viewing and downloading
- Metadata-based search
- File storage architecture

#### 2.1 Database Schema (Week 3)

**New Migrations:**
```sql
-- V002__create_collections.sql
CREATE TABLE collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_collections_active ON collections(is_active);
CREATE INDEX idx_collections_created_by ON collections(created_by);

-- V003__create_documents.sql
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT REFERENCES collections(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, ARCHIVED, DELETED
    current_version INT DEFAULT 1,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_versions (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_by BIGINT REFERENCES users(id),
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_current BOOLEAN DEFAULT true,
    UNIQUE(document_id, version_number)
);

CREATE TABLE document_metadata (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    product VARCHAR(255),
    revision VARCHAR(50),
    department VARCHAR(255),
    manufacturer VARCHAR(255),
    tags TEXT[], -- Array of tags
    category VARCHAR(255),
    effective_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id) -- One metadata record per document
);

-- Indexes for search performance
CREATE INDEX idx_documents_collection ON documents(collection_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_created_by ON documents(created_by);
CREATE INDEX idx_documents_created_at ON documents(created_at);
CREATE INDEX idx_doc_versions_document ON document_versions(document_id);
CREATE INDEX idx_doc_versions_current ON document_versions(is_current);
CREATE INDEX idx_metadata_product ON document_metadata(product);
CREATE INDEX idx_metadata_tags ON document_metadata USING GIN(tags);
CREATE INDEX idx_metadata_category ON document_metadata(category);
CREATE INDEX idx_metadata_department ON document_metadata(department);

-- V004__create_document_constraints.sql
-- Add proper constraints and validation
ALTER TABLE documents ADD CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED'));
ALTER TABLE document_versions ADD CONSTRAINT chk_version_number CHECK (version_number > 0);
ALTER TABLE document_versions ADD CONSTRAINT chk_file_size CHECK (file_size >= 0);
```

#### 2.2 File Storage Architecture (Week 3)

**Storage Structure:**
```
storage/
├── documents/
│   ├── originals/
│   │   └── {collection_id}/
│   │       └── {document_id}/
│   │           ├── v1.pdf
│   │           ├── v2.pdf
│   │           └── v3.pdf
│   ├── processed/ (Future - for AI processing)
│   └── archived/ (For archived documents)
└── uploads/ (Temporary upload staging)
```

**File Storage Service:**
- `FileStorageService.java` - Core file operations
  - `storeOriginalFile(MultipartFile, collectionId, documentId, version)`
  - `getFileStoragePath(collectionId, documentId, version, originalFilename)`
  - `deleteDocumentFiles(collectionId, documentId)`
  - `getFileSize(String filePath)`
  - `fileExists(String filePath)`
  - `deleteFile(String filePath)`

**Configuration:**
```yaml
# application.yaml
file-storage:
  base-path: ${FILE_STORAGE_BASE_PATH:/storage/documents}
  max-file-size: 50MB
  allowed-types:
    - application/pdf
    - application/vnd.openxmlformats-officedocument.wordprocessingml.document
    - text/plain
    - text/html
    - text/csv
    - text/markdown
```

#### 2.3 Backend API Development (Week 4)

**Collections API:**
```java
// CollectionController.java
GET    /api/v1/collections              - List all collections
POST   /api/v1/collections              - Create collection
GET    /api/v1/collections/{id}         - Get collection details
PUT    /api/v1/collections/{id}         - Update collection
DELETE /api/v1/collections/{id}        - Delete collection (soft delete)
GET    /api/v1/collections/{id}/documents - Get documents in collection
```

**Documents API:**
```java
// DocumentController.java
GET    /api/v1/documents                     - List/filter documents
POST   /api/v1/documents/upload             - Upload document (multipart)
GET    /api/v1/documents/{id}               - Get document details
PUT    /api/v1/documents/{id}               - Update document metadata
DELETE /api/v1/documents/{id}               - Archive document
GET    /api/v1/documents/{id}/download      - Download current version
GET    /api/v1/documents/{id}/versions       - Get version history
POST   /api/v1/documents/{id}/versions       - Upload new version
GET    /api/v1/documents/{id}/stream/{versionId} - Stream specific version
```

**Metadata API:**
```java
// MetadataController.java
GET    /api/v1/documents/{id}/metadata      - Get document metadata
PUT    /api/v1/documents/{id}/metadata      - Update metadata
GET    /api/v1/documents/search             - Metadata-based search
```

**Search API:**
```java
// SearchController.java
GET    /api/v1/search                       - Advanced metadata search
# Query parameters: title, collection, product, revision, tags, department, category
# Returns: List of matching documents with metadata
```

#### 2.4 Service Layer Implementation (Week 4)

**Core Services:**
```java
// CollectionService.java
- createCollection(CreateCollectionRequest, User)
- updateCollection(Long id, UpdateCollectionRequest, User)
- deleteCollection(Long id, User)
- getCollection(Long id)
- listCollections(boolean activeOnly)
- getDocumentsInCollection(Long collectionId, Pageable)

// DocumentService.java
- uploadDocument(UploadDocumentRequest, MultipartFile, User)
- getDocument(Long id)
- updateDocument(Long id, UpdateDocumentRequest, User)
- deleteDocument(Long id, User)
- listDocuments(DocumentFilter, Pageable)
- downloadDocument(Long id, Long versionId)

// MetadataService.java
- updateMetadata(Long documentId, MetadataRequest, User)
- getMetadata(Long documentId)
- searchDocuments(MetadataSearchRequest, Pageable)

// VersionService.java
- uploadNewVersion(Long documentId, MultipartFile, User)
- getVersionHistory(Long documentId)
- setCurrentVersion(Long documentId, int versionNumber, User)

// FileStorageService.java
- storeOriginalFile(MultipartFile, Long collectionId, Long documentId, int version)
- getFileStoragePath(Long collectionId, Long documentId, int version, String filename)
- deleteDocumentFiles(Long collectionId, Long documentId)
- serveFile(String filePath, HttpServletResponse)
```

#### 2.5 Frontend Development (Week 5)

**Core Pages:**
```
frontend/knowledgevault-ui/
├── app/
│   ├── (auth)/
│   │   ├── login/
│   │   └── layout.tsx
│   ├── dashboard/
│   │   ├── page.tsx                    - Main dashboard
│   │   └── layout.tsx
│   ├── collections/
│   │   ├── page.tsx                    - Collections list
│   │   ├── [id]/
│   │   │   └── page.tsx                - Collection details
│   │   └── new/
│   │       └── page.tsx                - Create collection
│   ├── documents/
│   │   ├── page.tsx                    - Documents list
│   │   ├── [id]/
│   │   │   ├── page.tsx                - Document details
│   │   │   ├── edit/
│   │   │   │   └── page.tsx            - Edit document/metadata
│   │   │   └── versions/
│   │   │       └── page.tsx            - Version history
│   │   ├── upload/
│   │   │   └── page.tsx                - Upload interface
│   │   └── search/
│   │       └── page.tsx                - Search interface
│   └── viewer/
│       └── [id]/
│           └── page.tsx                - PDF viewer
```

**Key Components:**
- `DocumentUpload.tsx` - Drag & drop upload with metadata form
- `DocumentViewer.tsx` - PDF viewer with navigation, zoom, search
- `DocumentList.tsx` - Filterable document list
- `CollectionCard.tsx` - Collection display cards
- `MetadataForm.tsx` - Dynamic metadata entry
- `SearchPanel.tsx` - Advanced search interface

**Document Viewer Features:**
- PDF.js integration for rendering
- Page navigation controls
- Zoom in/out functionality
- In-document text search
- Download original button
- Full metadata display
- Version history sidebar

#### 2.6 Testing & Integration (Week 5)

**Unit Testing:**
- Service layer unit tests
- Repository layer tests
- File storage service tests
- Controller validation tests

**Integration Testing:**
- Document upload end-to-end flow
- Version management workflow
- Search functionality testing
- Permission and authorization testing
- File storage cleanup testing

**Performance Testing:**
- Upload performance validation (<5s)
- Search response time (<2s)
- Document viewer load time (<3s)
- Concurrent upload testing
- Large file handling (up to 50MB)

#### 2.7 Phase 2 Deliverables

**Completed API Endpoints:**
- ✅ Collections CRUD (5 endpoints)
- ✅ Document upload/download (7 endpoints)
- ✅ Version management (3 endpoints)
- ✅ Metadata operations (3 endpoints)
- ✅ Search functionality (2 endpoints)

**Completed Frontend:**
- ✅ Login page (from Phase 1)
- ✅ Dashboard with statistics
- ✅ Collections management interface
- ✅ Document upload interface with drag & drop
- ✅ Document list with filtering
- ✅ Document detail page
- ✅ PDF viewer with all features
- ✅ Metadata editing interface
- ✅ Search interface

**Infrastructure:**
- ✅ File storage architecture
- ✅ Docker volume configuration for file storage
- ✅ Backup procedures for uploaded files
- ✅ Security for file operations

---

### Phase 3: AI Service Foundation

**Timeline:** Weeks 6-7  
**Objective:** Set up FastAPI service and basic document processing

**Scope:**
- FastAPI service setup
- Text extraction from documents
- Basic document chunking
- Ollama integration foundation

#### 3.1 FastAPI Service Setup (Week 6)

**Project Structure:**
```
ai-service/
├── app/
│   ├── main.py
│   ├── api/
│   │   ├── __init__.py
│   │   ├── health.py
│   │   └── ingest.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── config.py
│   │   └── security.py
│   ├── services/
│   │   ├── __init__.py
│   │   ├── extractor.py
│   │   ├── chunker.py
│   │   └── ollama_client.py
│   └── models/
│       ├── __init__.py
│       └── schemas.py
├── requirements.txt
├── Dockerfile
└── tests/
```

**Requirements.txt:**
```
fastapi==0.104.1
uvicorn[standard]==0.24.0
pydantic==2.5.0
pydantic-settings==2.1.0
sqlalchemy==2.0.23
asyncpg==0.29.0
python-multipart==0.0.6
aiofiles==23.2.1
pypdf==0.10.3
python-docx==1.1.0
beautifulsoup4==4.12.2
pandas==2.1.3
httpx==0.25.2
```

**Docker Configuration:**
```yaml
# docker-compose.yml addition
ai-service:
  build: ./ai-service
  container_name: knowledgevault-ai
  ports:
    - "8000:8000"
  environment:
    - DATABASE_URL=postgresql+asyncpg://postgres:postgres@postgres:5432/knowledgevault
    - OLLAMA_BASE_URL=http://ollama:11434
    - INTERNAL_API_KEY=${INTERNAL_API_KEY}
  depends_on:
    - postgres
    - ollama
  networks:
    - knowledgevault-network
  restart: unless-stopped
```

#### 3.2 Document Processing Pipeline (Week 6)

**Text Extraction Service:**
```python
# extractor.py
class DocumentExtractor:
    async def extract_text(file_path: str, mime_type: str) -> str:
        # PDF extraction with pypdf
        # DOCX extraction with python-docx
        # TXT/HTML/CSV handling
        # Preserve page numbers and sections
```

**Document Chunking Service:**
```python
# chunker.py
class DocumentChunker:
    async def chunk_document(
        text: str, 
        chunk_size: int = 1000,
        overlap: int = 200
    ) -> List[DocumentChunk]:
        # Recursive text splitting
        # Preserve metadata (page numbers, sections)
        # Handle boundaries intelligently
```

#### 3.3 Ollama Integration (Week 7)

**Ollama Client:**
```python
# ollama_client.py
class OllamaClient:
    async def generate_embedding(text: str, model: str) -> List[float]:
        # Support for nomic-embed-text, bge-m3, mxbai-embed-large
    
    async def chat_completion(messages: List[Message], model: str) -> str:
        # Support for Qwen, Llama, Gemma, DeepSeek
```

**Ollama Docker Service:**
```yaml
ollama:
  image: ollama/ollama:latest
  container_name: knowledgevault-ollama
  ports:
    - "11434:11434"
  volumes:
    - ollama_models:/root/.ollama
  environment:
    - OLLAMA_MODELS=qwen2:7b,bge-m3,nomic-embed-text
  restart: unless-stopped
```

#### 3.4 Phase 3 Deliverables

- ✅ FastAPI service running and accessible
- ✅ Health check endpoint
- ✅ Text extraction from PDF, DOCX, TXT, HTML, CSV
- ✅ Document chunking with overlap
- ✅ Ollama integration and testing
- ✅ Internal API authentication
- ✅ Docker deployment integration

---

### Phase 4: Vector Storage & Embeddings

**Timeline:** Weeks 8-9  
**Objective:** Store document chunks and generate embeddings

**Scope:**
- Database schema for chunks and embeddings
- Embedding generation pipeline
- Batch processing capabilities
- Integration with Spring Boot document upload

#### 4.1 Database Schema Extensions

```sql
-- V005__create_chunks_embeddings.sql
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    version_id BIGINT REFERENCES document_versions(id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    page_number INT,
    section_name VARCHAR(255),
    token_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, version_id, chunk_index)
);

CREATE TABLE embeddings (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT REFERENCES document_chunks(id) ON DELETE CASCADE,
    model_name VARCHAR(100) NOT NULL,
    embedding vector(1024), -- Default dimension, will vary by model
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(chunk_id, model_name)
);

-- Indexes for vector similarity search
CREATE INDEX idx_chunks_document ON document_chunks(document_id);
CREATE INDEX idx_chunks_version ON document_chunks(version_id);
CREATE INDEX idx_embeddings_model ON embeddings(model_name);
CREATE INDEX idx_embeddings_chunk ON embeddings(chunk_id);

-- Vector index for similarity search (HNSW for performance)
CREATE INDEX idx_embeddings_vector ON embeddings 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);
```

#### 4.2 Embedding Generation Pipeline

**FastAPI Endpoints:**
```python
# ingest.py
@router.post("/ingest")
async def start_ingestion(document_id: int, version_id: int):
    # 1. Extract text from document
    # 2. Chunk the content
    # 3. Generate embeddings
    # 4. Store in database
    # Return job_id for tracking

@router.get("/ingest/{job_id}")
async def get_ingestion_status(job_id: str):
    # Return progress and status
```

**Batch Processing:**
```python
class EmbeddingService:
    async def process_document_batch(
        document_ids: List[int], 
        batch_size: int = 10
    ) -> List[IngestionResult]:
        # Process multiple documents in parallel
        # Rate limiting for Ollama API
        # Error handling and retry logic
```

#### 4.3 Spring Boot Integration

**Document Upload Enhancement:**
```java
// DocumentService.java - Enhanced upload method
public DocumentUploadResponse uploadDocumentWithAI(UploadRequest request) {
    // 1. Store original file (existing)
    // 2. Create metadata records (existing)
    // 3. Trigger AI processing (new)
    aiServiceClient.triggerIngestion(documentId, versionId);
    // 4. Return with processing status
}

// AIServiceClient.java
public void triggerIngestion(Long documentId, Long versionId) {
    // Internal API call to FastAPI
    // Use X-Internal-Token for authentication
}

// DocumentStatusController.java
@GetMapping("/documents/{id}/processing-status")
public ProcessingStatus getProcessingStatus(@PathVariable Long id) {
    // Poll AI service for ingestion status
    // Return progress information
}
```

#### 4.4 Phase 4 Deliverables

- ✅ Database tables for chunks and embeddings
- ✅ Embedding generation pipeline
- ✅ Integration with document upload flow
- ✅ Batch processing capabilities
- ✅ Processing status tracking
- ✅ Vector similarity search indexes

---

### Phase 5: Semantic Search & RAG

**Timeline:** Weeks 10-11  
**Objective:** Implement semantic search and RAG chat capabilities

**Scope:**
- Hybrid search (keyword + vector)
- RAG pipeline implementation
- AI chat interface
- Source citations

#### 5.1 Search Implementation

**Vector Similarity Search:**
```python
# search.py
class SearchService:
    async def vector_search(
        query: str,
        collection_ids: List[int] = None,
        limit: int = 10,
        model: str = "bge-m3"
    ) -> List[SearchResult]:
        # 1. Generate query embedding
        # 2. Perform vector similarity search
        # 3. Return ranked results with scores
        
    async def hybrid_search(
        query: str,
        collection_ids: List[int] = None,
        limit: int = 10
    ) -> List[SearchResult]:
        # 1. Vector similarity search
        # 2. PostgreSQL full-text search
        # 3. Reciprocal rank fusion
        # 4. Return combined results
```

#### 5.2 RAG Pipeline

**RAG Service:**
```python
# rag.py
class RAGService:
    async def answer_question(
        question: str,
        collection_ids: List[int] = None,
        chat_history: List[Message] = None
    ) -> StreamResponse:
        # 1. Retrieve relevant chunks
        # 2. Assemble context with citations
        # 3. Generate answer with LLM
        # 4. Stream response with metadata
        
    async def assemble_context(
        chunks: List[DocumentChunk]
    ) -> str:
        # Format context with proper citations
        # Include document, version, page, section
```

#### 5.3 Frontend Development

**Search Interface:**
```typescript
// app/search/page.tsx
- Advanced search interface
- Semantic vs keyword vs hybrid toggle
- Collection filtering
- Result display with relevance scores
- Preview snippets with highlighting
```

**Chat Interface:**
```typescript
//app/chat/page.tsx
- Chat input interface
- Streaming response display
- Source citation cards
- Document viewer integration
- Chat history management
```

#### 5.4 API Endpoints

**Spring Boot Proxy Endpoints:**
```java
// SearchController.java - Enhanced
@PostMapping("/search/semantic")
public List<SearchResult> semanticSearch(@RequestBody SearchRequest request) {
    // Proxy to FastAPI with authentication
    // Audit logging
    // Return results
}

@PostMapping("/chat")
public SseEmitter chat(@RequestBody ChatRequest request) {
    // Proxy to FastAPI with streaming support
    // Return Server-Sent Events stream
}
```

#### 5.5 Phase 5 Deliverables

- ✅ Semantic search functionality
- ✅ Hybrid search (keyword + vector)
- ✅ RAG pipeline with citations
- ✅ AI chat interface
- ✅ Streaming responses
- ✅ Source document integration
- ✅ Chat history management

---

### Phase 6: Advanced Features & Polish

**Timeline:** Weeks 12-13  
**Objective:** Add advanced features and polish the platform

**Scope:**
- OCR support
- Admin dashboard enhancements
- Advanced analytics
- Performance optimization

#### 6.1 OCR Support

**OCR Integration:**
```python
# ocr.py
class OCRService:
    async def process_scanned_document(file_path: str) -> str:
        # Tesseract or EasyOCR integration
        # Handle multi-page documents
        # Preserve layout information
```

#### 6.2 Admin Dashboard

**Enhanced Metrics:**
- Document processing statistics
- Storage usage analytics
- AI usage metrics
- User activity reports
- System health monitoring

#### 6.3 Performance Optimization

**Caching Strategy:**
- Redis for session caching
- Query result caching
- API response caching

**Database Optimization:**
- Index tuning for large datasets
- Query optimization
- Connection pooling

#### 6.4 Phase 6 Deliverables

- ✅ OCR support for scanned documents
- ✅ Enhanced admin dashboard
- ✅ Performance optimization
- ✅ Caching implementation
- ✅ Backup/automation scripts

---

### Phase 7: Production Readiness

**Timeline:** Week 14  
**Objective:** Hardening, documentation, deployment

**Scope:**
- Security hardening
- API documentation
- Deployment automation
- Monitoring and alerting

#### 7.1 Security Hardening

**Security Measures:**
- HTTPS/TLS configuration
- Rate limiting
- Input validation
- SQL injection prevention
- XSS protection
- CSRF protection

#### 7.2 API Documentation

**OpenAPI/Swagger:**
- Spring Boot API documentation
- FastAPI automatic documentation
- Integration guides

#### 7.3 Deployment Automation

**Infrastructure:**
- Docker Compose production configuration
- Nginx reverse proxy configuration
- SSL certificate automation
- Backup automation scripts

#### 7.4 Monitoring

**Observability:**
- Application logging
- Metrics collection (Prometheus)
- Health checks
- Alert configuration

#### 7.5 Phase 7 Deliverables

- ✅ Production-ready Docker configuration
- ✅ Security hardening completed
- ✅ Complete API documentation
- ✅ Deployment guides
- ✅ Monitoring and alerting
- ✅ Backup and restore procedures

---

## Technical Decisions & Rationale

### 1. Document Storage First Approach

**Decision:** Implement complete document storage before AI capabilities.

**Rationale:**
- Users get immediate value from document management
- Proves core functionality before adding complexity
- AI features become value-add rather than core requirement
- Easier to test and validate incrementally

### 2. File Storage Architecture

**Decision:** Store files outside database in structured hierarchy.

**Rationale:**
- Database remains lightweight and fast
- Files can be served directly by web server
- Easier backup and migration
- Supports future cloud storage integration
- Better performance for large files

### 3. Separation of Concerns

**Decision:** Clear separation between document management and AI processing.

**Rationale:**
- AI service can be swapped without affecting business data
- Document storage works independently of AI availability
- Easier to maintain and extend
- Better testing isolation

### 4. Technology Stack Choices

**Decision:** Maintain Spring Boot + FastAPI + PostgreSQL stack.

**Rationale:**
- Proven, enterprise-ready technologies
- Strong community support
- Good Docker support
- Fits target market (SMB to enterprise)

---

## Risk Assessment & Mitigation

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| File storage volume exhaustion | Medium | High | Implement storage monitoring, automatic cleanup policies, cloud storage integration option |
| Large file upload timeouts | Medium | Medium | Implement chunked uploads, progress indicators, async processing |
| Database performance with 50k documents | Low | High | Proper indexing from start, query optimization, connection pooling |
| AI service availability affecting core functions | Medium | High | Document storage independent of AI, graceful degradation, queue system |
| pgvector scaling issues | Medium | Medium | Start with proper indexing, benchmark at scale, have fallback strategies |
| Ollama performance on CPU-only deployments | High | Medium | Support smaller models, GPU recommendations, cloud AI service fallback |

---

## Definition of Done (MVP)

The complete MVP is finished when:

**Document Management:**
- ✅ Users can upload documents (PDF, DOCX, TXT, HTML, CSV)
- ✅ Users can organize documents into collections
- ✅ Users can manage document versions
- ✅ Users can edit and search metadata
- ✅ Users can view and download documents
- ✅ Role-based permissions enforced

**AI Capabilities:**
- ✅ Documents are processed for AI search
- ✅ Users can perform semantic search
- ✅ Users can chat with AI assistant
- ✅ All AI responses include source citations
- ✅ Users can verify answers in source documents

**Platform Features:**
- ✅ Authentication and authorization working
- ✅ Audit logging complete
- ✅ Admin dashboard functional
- ✅ White-label branding configurable
- ✅ License management operational
- ✅ Docker deployment successful

**Performance:**
- ✅ Upload acknowledgement <5 seconds
- ✅ Search results <2 seconds
- ✅ AI responses <10 seconds
- ✅ Document viewer load <3 seconds

**Quality:**
- ✅ Security hardening completed
- ✅ API documentation comprehensive
- ✅ Deployment guides complete
- ✅ Backup procedures tested
- ✅ Monitoring and alerting configured

---

## Success Metrics

**Technical Metrics:**
- Indexing success rate >99%
- API response times meet NFR requirements
- System uptime >99.5%
- Zero data loss incidents

**Business Metrics:**
- User satisfaction >4.5/5
- Search usage increases over time
- Document upload volume grows
- AI chat adoption increases

**Quality Metrics:**
- Citation coverage 100%
- Search result relevance >85%
- AI answer accuracy >80%
- System security audit passed

---

## Immediate Next Steps

**Current Focus:** Phase 2 - Document Storage Module

**Week 3 Priorities:**
1. Create database migrations for collections and documents
2. Implement file storage service and architecture
3. Set up Docker volume for file storage
4. Create repository layer for collections and documents

**Week 4 Priorities:**
1. Implement service layer for document management
2. Create REST API controllers for collections and documents
3. Implement file upload/download endpoints
4. Add comprehensive error handling

**Week 5 Priorities:**
1. Build frontend document management interface
2. Implement PDF viewer with all features
3. Create search and filtering interface
4. Integration testing and performance validation

---

## Appendix: File Structure Reference

**Backend Phase 2 Additions:**
```
backend/document-service/src/main/java/com/kva/document_service/
├── collections/
│   ├── CollectionController.java
│   ├── CollectionService.java
│   ├── CollectionRepository.java
│   ├── Collection.java
│   └── dto/
│       ├── CreateCollectionRequest.java
│       ├── UpdateCollectionRequest.java
│       └── CollectionResponse.java
├── documents/
│   ├── DocumentController.java
│   ├── DocumentService.java
│   ├── DocumentRepository.java
│   ├── Document.java
│   ├── DocumentVersion.java
│   └── dto/
│       ├── UploadDocumentRequest.java
│       ├── DocumentResponse.java
│       └── DocumentFilter.java
├── metadata/
│   ├── MetadataService.java
│   ├── MetadataRepository.java
│   ├── DocumentMetadata.java
│   └── dto/
│       ├── MetadataRequest.java
│       └── MetadataSearchRequest.java
├── storage/
│   ├── FileStorageService.java
│   ├── FileStorageProperties.java
│   └── StorageException.java
└── versions/
    ├── VersionService.java
    ├── VersionRepository.java
    └── dto/
        └── VersionResponse.java
```

**Database Migrations:**
```
backend/document-service/src/main/resources/db/migration/
├── V001__create_users_roles.sql ✅ (Complete)
├── V002__create_collections.sql (New)
├── V003__create_documents.sql (New)
├── V004__create_document_constraints.sql (New)
├── V005__create_chunks_embeddings.sql (Phase 4)
└── V006__create_licensing_branding.sql (Phase 6)
```

---

**End of Revised Implementation Plan v2.0**

**Last Updated:** June 18, 2026  
**Status:** Ready for Phase 2 Implementation  
**Next Review:** End of Phase 2 (Week 5)