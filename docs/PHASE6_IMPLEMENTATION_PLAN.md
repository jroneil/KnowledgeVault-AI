# Phase 6: Advanced Features & Polish - Implementation Plan

**Date:** June 23, 2026  
**Status:** 🚀 In Progress  
**Duration:** 2 Weeks  
**Following:** Phase 5 (Advanced Search & Analytics - Completed)

---

## Executive Summary

Phase 6 focuses on adding advanced features that enhance the platform's capabilities and polish the user experience. This phase introduces OCR support for scanned documents, implements an enhanced admin dashboard with comprehensive analytics, adds Redis-based distributed caching for performance optimization, and creates backup/automation scripts for production readiness.

### Key Objectives

1. **OCR Support** - Enable processing of scanned PDFs and images
2. **Enhanced Admin Dashboard** - Comprehensive analytics and monitoring
3. **Performance Optimization** - Redis caching and query optimization
4. **Production Automation** - Backup scripts and monitoring

---

## Architecture Updates

### Phase 6 Architecture

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   Next.js 16     │────▶│  Spring Boot     │────▶│   FastAPI       │
│   Frontend       │ REST│  Document Service│ REST│  AI Service     │
│   (port 3000)    │◀────│   (port 8080)    │◀────│   (port 8000)   │
│   - OCR Preview  │     │   - Admin API    │     │   - OCR Service │
│   - Analytics UI │     │   - Monitoring   │     │   - Advanced AI │
└──────────────────┘     └────────┬─────────┘     └────────┬─────────┘
                                   │                        │
                                   ▼                        ▼
                          ┌──────────────────────────────────┐
                          │   PostgreSQL 16 + pgvector       │
                          │      (port 5432)                 │
                          └────────────┬─────────────────────┘
                                       │
                                       ▼
                          ┌──────────────────────────────────┐
                          │   Redis (Caching Layer)         │
                          │      (port 6379)                 │
                          │   - Query Results                │
                          │   - Sessions                     │
                          │   - API Responses                │
                          └──────────────────────────────────┘
```

---

## Implementation Tasks

### Task 1: OCR Support Implementation

**Timeline:** Days 1-3  
**Priority:** High

#### 1.1 OCR Service Setup

**File:** `ai-service/app/services/ocr_service.py`

**Dependencies:**
```python
pytesseract==0.3.10
pillow==10.1.0
pdf2image==1.16.3
```

**Features:**
- PDF to image conversion for OCR
- Tesseract OCR integration
- Multi-page document support
- Layout preservation
- Language detection and support

**API:**
```python
class OCRService:
    async def process_scanned_document(
        file_path: str,
        output_format: str = "text",
        languages: List[str] = None
    ) -> OCRResult:
        """
        Process scanned document with OCR.
        
        Args:
            file_path: Path to PDF or image file
            output_format: "text", "hocr", or "xml"
            languages: List of language codes (e.g., ["eng", "fra"])
        
        Returns:
            OCRResult with extracted text, confidence scores, and layout
        """
    
    async def process_pdf_with_mixed_content(
        file_path: str
    ) -> MixedContentResult:
        """
        Detect and process PDFs with both text and scanned content.
        
        Returns:
            MixedContentResult indicating text vs. scanned pages
        """
    
    def get_supported_languages() -> List[LanguageInfo]:
        """Return list of available OCR languages."""
```

#### 1.2 Docker OCR Setup

**Dockerfile Addition:**
```dockerfile
# Install Tesseract OCR
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-fre \
    tesseract-ocr-deu \
    tesseract-ocr-spa \
    libtesseract-dev \
    libleptonica-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*
```

**Environment Variables:**
```bash
TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata
OCR_LANGUAGES=eng,fre,deu,spa
OCR_DPI=300
OCR_TIMEOUT=120
```

#### 1.3 OCR API Endpoints

**File:** `ai-service/app/api/ocr.py`

**Endpoints:**
```python
POST /api/v1/ocr/process
Request:
{
  "file_path": "/path/to/document.pdf",
  "output_format": "text",
  "languages": ["eng", "fra"]
}
Response:
{
  "text": "Extracted text...",
  "confidence": 0.95,
  "pages_processed": 10,
  "processing_time_ms": 15000,
  "language_detected": "eng"
}

POST /api/v1/ocr/detect-mixed
Request:
{
  "file_path": "/path/to/document.pdf"
}
Response:
{
  "total_pages": 20,
  "text_pages": [1, 2, 3, 5, 6, 7, 9, 10],
  "scanned_pages": [4, 8, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20],
  "needs_ocr": true
}
```

#### 1.4 Enhanced Document Processing

**File:** `ai-service/app/services/extractor.py` (Enhanced)

**Updates:**
```python
class DocumentExtractor:
    async def extract_text_smart(
        file_path: str,
        mime_type: str,
        enable_ocr: bool = True
    ) -> ExtractedText:
        """
        Smart extraction with automatic OCR detection.
        
        1. Try standard text extraction
        2. If empty or low confidence, use OCR
        3. Merge results for mixed content PDFs
        """
```

---

### Task 2: Enhanced Admin Dashboard

**Timeline:** Days 4-6  
**Priority:** High

#### 2.1 Backend Admin API

**File:** `backend/document-service/src/main/java/com/kva/document_service/admin/AdminController.java`

**Endpoints:**
```java
// System Statistics
@GetMapping("/api/v1/admin/stats")
public AdminStats getSystemStats()

// Document Processing Stats
@GetMapping("/api/v1/admin/documents/stats")
public DocumentStats getDocumentStats(
    @RequestParam LocalDate startDate,
    @RequestParam LocalDate endDate
)

// Storage Analytics
@GetMapping("/api/v1/admin/storage")
public StorageAnalytics getStorageAnalytics()

// AI Usage Metrics
@GetMapping("/api/v1/admin/ai/metrics")
public AIMetrics getAIMetrics(
    @RequestParam LocalDate startDate,
    @RequestParam LocalDate endDate
)

// User Activity Reports
@GetMapping("/api/v1/admin/users/activity")
public UserActivityReport getUserActivityReport(
    @RequestParam LocalDate startDate,
    @RequestParam LocalDate endDate
)

// Search Analytics
@GetMapping("/api/v1/admin/search/analytics")
public SearchAnalyticsReport getSearchAnalytics(
    @RequestParam LocalDate startDate,
    @RequestParam LocalDate endDate
)

// System Health
@GetMapping("/api/v1/admin/health")
public SystemHealth getSystemHealth()

// Performance Metrics
@GetMapping("/api/v1/admin/performance")
public PerformanceMetrics getPerformanceMetrics()
```

#### 2.2 Admin DTOs

**File:** `backend/document-service/src/main/java/com/kva/document_service/admin/dto/`

**DTO Classes:**
```java
AdminStats.java
{
    totalDocuments: Long
    totalUsers: Long
    totalCollections: Long
    storageUsed: Long
    storageAvailable: Long
    documentsProcessedToday: Long
    activeUsersToday: Long
    systemUptime: Long
    databaseConnections: Int
}

DocumentStats.java
{
    totalDocuments: Long
    documentsUploaded: Map<LocalDate, Long>
    documentsByType: Map<String, Long>
    documentsByStatus: Map<String, Long>
    processingSuccessRate: Double
    avgProcessingTimeMs: Double
}

StorageAnalytics.java
{
    totalStorageUsed: Long
    storageByCollection: Map<String, Long>
    storageByType: Map<String, Long>
    largestDocuments: List<DocumentInfo>
    storageGrowthTrend: List<StorageDataPoint>
}

AIMetrics.java
{
    totalQueries: Long
    queriesByType: Map<String, Long>
    avgResponseTimeMs: Double
    cacheHitRate: Double
    topQueries: List<QueryInfo>
    modelsUsed: Map<String, Long>
}

UserActivityReport.java
{
    activeUsers: Long
    usersByActivityLevel: Map<String, Long>
    topContributors: List<UserInfo>
    activityByDay: Map<LocalDate, Long>
    mostViewedDocuments: List<DocumentInfo>
}
```

#### 2.3 Frontend Admin Dashboard

**File:** `frontend/knowledgevault-ui/app/admin/page.tsx`

**Components:**
```typescript
// AdminDashboard.tsx
- Overview cards (stats)
- Document processing chart
- Storage usage visualization
- AI usage metrics
- User activity heatmap
- System health status
- Performance graphs

// DocumentStatsCard.tsx
- Documents uploaded over time
- Processing success rate
- Average processing time
- Documents by type/status

// StorageAnalyticsCard.tsx
- Total storage used/available
- Storage by collection
- Storage growth trend
- Largest documents

// AIAnalyticsCard.tsx
- Query volume chart
- Query type distribution
- Response time metrics
- Cache performance
- Top queries

// UserActivityCard.tsx
- Active users count
- Activity by day
- Top contributors
- Most viewed documents

// SystemHealthCard.tsx
- Service status indicators
- Database health
- AI service health
- Storage health
- Uptime metrics

// PerformanceMetricsCard.tsx
- API response times
- Database query times
- Memory usage
- CPU usage
- Request rate
```

#### 2.4 Real-time Updates

**WebSocket Integration:**
```typescript
// lib/adminWebSocket.ts
- Connect to admin WebSocket
- Real-time stats updates
- Live query monitoring
- System alerts

// LiveQueryMonitor.tsx
- Real-time query display
- Query type badges
- Response time indicators
- Source document links
```

---

### Task 3: Performance Optimization

**Timeline:** Days 7-9  
**Priority:** High

#### 3.1 Redis Integration

**Docker Compose Addition:**
```yaml
redis:
  image: redis:7-alpine
  container_name: knowledgevault-redis
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
  command: redis-server --appendonly yes
  networks:
    - knowledgevault-network
  restart: unless-stopped

volumes:
  redis_data:
```

**Configuration:**
```yaml
# application.yaml
spring:
  redis:
    host: redis
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms

# ai-service/.env
REDIS_URL=redis://redis:6379/0
REDIS_PASSWORD=
REDIS_MAX_CONNECTIONS=10
REDIS_TTL=3600
```

#### 3.2 Redis Cache Service

**File:** `backend/document-service/src/main/java/com/kva/document_service/cache/RedisCacheService.java`

**Features:**
```java
@Service
public class RedisCacheService {
    
    // Query result caching
    public void cacheSearchResults(String key, SearchResults results, long ttl);
    public SearchResults getCachedSearchResults(String key);
    
    // Document metadata caching
    public void cacheDocumentMetadata(Long docId, DocumentMetadata metadata);
    public DocumentMetadata getCachedDocumentMetadata(Long docId);
    
    // Session caching
    public void cacheUserSession(String sessionId, UserSession session);
    public UserSession getCachedUserSession(String sessionId);
    
    // API response caching
    public void cacheAPIResponse(String key, Object response, long ttl);
    public Object getCachedAPIResponse(String key);
    
    // Cache invalidation
    public void invalidateDocumentCache(Long docId);
    public void invalidateUserCache(Long userId);
    public void clearAllCache();
    
    // Cache statistics
    public CacheStatistics getCacheStats();
}
```

#### 3.3 FastAPI Redis Integration

**File:** `ai-service/app/services/redis_cache_service.py`

**Implementation:**
```python
import redis.asyncio as redis
from typing import Optional, Any
import json
import pickle

class RedisCacheService:
    """Distributed caching service using Redis."""
    
    def __init__(self):
        self.redis = redis.from_url(
            settings.REDIS_URL,
            encoding="utf-8",
            decode_responses=True
        )
        self.ttl = settings.REDIS_TTL
    
    async def cache_search_results(
        self,
        key: str,
        results: Any,
        ttl: Optional[int] = None
    ) -> None:
        """Cache search results."""
        serialized = pickle.dumps(results)
        await self.redis.setex(key, ttl or self.ttl, serialized)
    
    async def get_cached_search_results(
        self,
        key: str
    ) -> Optional[Any]:
        """Get cached search results."""
        cached = await self.redis.get(key)
        if cached:
            return pickle.loads(cached)
        return None
    
    async def cache_embedding(
        self,
        text_hash: str,
        embedding: List[float],
        ttl: int = 86400
    ) -> None:
        """Cache text embedding for 24 hours."""
        await self.redis.setex(
            f"emb:{text_hash}",
            ttl,
            json.dumps(embedding)
        )
    
    async def get_cached_embedding(
        self,
        text_hash: str
    ) -> Optional[List[float]]:
        """Get cached embedding."""
        cached = await self.redis.get(f"emb:{text_hash}")
        if cached:
            return json.loads(cached)
        return None
    
    async def invalidate_pattern(self, pattern: str) -> int:
        """Invalidate keys matching pattern."""
        keys = []
        async for key in self.redis.scan_iter(match=pattern):
            keys.append(key)
        if keys:
            return await self.redis.delete(*keys)
        return 0
    
    async def get_cache_stats(self) -> Dict:
        """Get cache statistics."""
        info = await self.redis.info()
        return {
            "used_memory": info.get("used_memory_human"),
            "total_keys": info.get("db0", {}).get("keys", 0),
            "hits": info.get("keyspace_hits", 0),
            "misses": info.get("keyspace_misses", 0),
            "hit_rate": self._calculate_hit_rate(info)
        }
```

#### 3.4 Database Optimization

**Index Optimization:**
```sql
-- Add composite indexes for common queries
CREATE INDEX idx_doc_search_composite ON documents(collection_id, status, created_at DESC);
CREATE INDEX idx_metadata_search ON document_metadata(product, category, effective_date);
CREATE INDEX idx_chunks_search ON document_chunks(document_id, version_id, chunk_index);

-- Analyze tables for query optimization
ANALYZE documents;
ANALYZE document_versions;
ANALYZE document_metadata;
ANALYZE document_chunks;
ANALYZE embeddings;
```

**Connection Pool Tuning:**
```yaml
# application.yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
```

---

### Task 4: Backup & Automation Scripts

**Timeline:** Days 10-12  
**Priority:** Medium

#### 4.1 Database Backup Script

**File:** `scripts/backup-database.sh`

```bash
#!/bin/bash

# Database backup script
BACKUP_DIR="/backups/database"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/knowledgevault_$DATE.sql.gz"

# Create backup directory
mkdir -p $BACKUP_DIR

# Backup database
docker exec knowledgevault-postgres pg_dump -U postgres knowledgevault | gzip > $BACKUP_FILE

# Keep last 30 days
find $BACKUP_DIR -name "knowledgevault_*.sql.gz" -mtime +30 -delete

echo "Backup completed: $BACKUP_FILE"
```

#### 4.2 File Storage Backup Script

**File:** `scripts/backup-files.sh`

```bash
#!/bin/bash

# File storage backup script
SOURCE_DIR="/storage/documents"
BACKUP_DIR="/backups/files"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/documents_$DATE.tar.gz"

# Create backup directory
mkdir -p $BACKUP_DIR

# Backup files
tar -czf $BACKUP_FILE -C /storage documents

# Keep last 30 days
find $BACKUP_DIR -name "documents_*.tar.gz" -mtime +30 -delete

echo "Backup completed: $BACKUP_FILE"
```

#### 4.3 Redis Backup Script

**File:** `scripts/backup-redis.sh`

```bash
#!/bin/bash

# Redis backup script
BACKUP_DIR="/backups/redis"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/redis_$DATE.rdb"

# Create backup directory
mkdir -p $BACKUP_DIR

# Trigger Redis save
docker exec knowledgevault-redis redis-cli BGSAVE

# Wait for save to complete
sleep 5

# Copy RDB file
docker cp knowledgevault-redis:/data/dump.rdb $BACKUP_FILE

# Keep last 7 days (Redis is ephemeral cache)
find $BACKUP_DIR -name "redis_*.rdb" -mtime +7 -delete

echo "Backup completed: $BACKUP_FILE"
```

#### 4.4 Automated Backup Cron Jobs

**File:** `scripts/setup-cron.sh`

```bash
#!/bin/bash

# Setup automated backups

# Database backup (daily at 2 AM)
echo "0 2 * * * /scripts/backup-database.sh" | crontab -

# File storage backup (daily at 3 AM)
echo "0 3 * * * /scripts/backup-files.sh" | crontab -

# Redis backup (hourly)
echo "0 * * * * /scripts/backup-redis.sh" | crontab -

# Cleanup old logs (weekly on Sunday at 4 AM)
echo "0 4 * * 0 /scripts/cleanup-logs.sh" | crontab -

echo "Cron jobs configured successfully"
```

#### 4.5 Restore Scripts

**File:** `scripts/restore-database.sh`

```bash
#!/bin/bash

# Database restore script
BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup_file>"
    exit 1
fi

# Restore database
gunzip -c $BACKUP_FILE | docker exec -i knowledgevault-postgres psql -U postgres knowledgevault

echo "Restore completed from: $BACKUP_FILE"
```

#### 4.6 Monitoring Script

**File:** `scripts/monitor-system.sh`

```bash
#!/bin/bash

# System monitoring script
ALERT_EMAIL="admin@knowledgevault.ai"

# Check disk space
DISK_USAGE=$(df -h /storage | awk 'NR==2 {print $5}' | sed 's/%//')
if [ $DISK_USAGE -gt 80 ]; then
    echo "WARNING: Disk usage at ${DISK_USAGE}%" | mail -s "Disk Space Alert" $ALERT_EMAIL
fi

# Check database connections
DB_CONNECTIONS=$(docker exec knowledgevault-postgres psql -U postgres -t -c "SELECT count(*) FROM pg_stat_activity;")
if [ $DB_CONNECTIONS -gt 50 ]; then
    echo "WARNING: High database connections: ${DB_CONNECTIONS}" | mail -s "Database Alert" $ALERT_EMAIL
fi

# Check service health
SERVICES=("knowledgevault-postgres" "knowledgevault-ai" "knowledgevault-backend" "knowledgevault-redis")
for service in "${SERVICES[@]}"; do
    if ! docker ps | grep -q $service; then
        echo "ERROR: Service $service is not running" | mail -s "Service Down Alert" $ALERT_EMAIL
    fi
done

echo "Monitoring check completed"
```

---

### Task 5: Enhanced Security & Rate Limiting

**Timeline:** Days 13-14  
**Priority:** Medium

#### 5.1 Rate Limiting Implementation

**File:** `backend/document-service/src/main/java/com/kva/document_service/security/RateLimitingFilter.java`

```java
@Component
public class RateLimitingFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientId = getClientId(httpRequest);
        String endpoint = httpRequest.getRequestURI();
        
        if (!isRateLimited(clientId, endpoint)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429);
            httpResponse.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
        }
    }
    
    private boolean isRateLimited(String clientId, String endpoint) {
        String key = "ratelimit:" + endpoint + ":" + clientId;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        
        int limit = getLimitForEndpoint(endpoint);
        return count != null && count > limit;
    }
    
    private int getLimitForEndpoint(String endpoint) {
        if (endpoint.contains("/search/")) return 30;  // 30 requests/min
        if (endpoint.contains("/api/v1/documents/")) return 20;
        return 100;  // Default limit
    }
}
```

#### 5.2 Enhanced Logging

**File:** `backend/document-service/src/main/java/com/kva/document_service/logging/EnhancedLoggingConfig.java`

```java
@Configuration
public class EnhancedLoggingConfig {
    
    @Bean
    public Logger structuredLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("com.kva").setLevel(Level.INFO);
        return context.getLogger("ROOT");
    }
    
    @Bean
    public AsyncAppender asyncAppender() {
        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setQueueSize(1000);
        asyncAppender.setDiscardingThreshold(0);
        return asyncAppender;
    }
}
```

---

## Testing Strategy

### OCR Testing

```bash
# Test OCR with scanned PDF
curl -X POST http://localhost:8000/api/v1/ocr/process \
  -H "Authorization: Bearer YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "file_path": "/storage/scanned.pdf",
    "output_format": "text",
    "languages": ["eng"]
  }'

# Test mixed content detection
curl -X POST http://localhost:8000/api/v1/ocr/detect-mixed \
  -H "Authorization: Bearer YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "file_path": "/storage/mixed.pdf"
  }'
```

### Admin Dashboard Testing

```bash
# Get system stats
curl -X GET http://localhost:8080/api/v1/admin/stats \
  -H "Authorization: Bearer YOUR_JWT"

# Get document stats
curl -X GET "http://localhost:8080/api/v1/admin/documents/stats?startDate=2026-06-01&endDate=2026-06-30" \
  -H "Authorization: Bearer YOUR_JWT"

# Get AI metrics
curl -X GET "http://localhost:8080/api/v1/admin/ai/metrics?startDate=2026-06-01&endDate=2026-06-30" \
  -H "Authorization: Bearer YOUR_JWT"
```

### Redis Cache Testing

```bash
# Test cache set/get
docker exec knowledgevault-redis redis-cli SET test_key "test_value"
docker exec knowledgevault-redis redis-cli GET test_key

# Test cache expiration
docker exec knowledgevault-redis redis-cli SETEX expire_key 60 "expires_in_60s"

# Test cache stats
docker exec knowledgevault-redis redis-cli INFO
```

### Backup Scripts Testing

```bash
# Test database backup
./scripts/backup-database.sh

# Test file backup
./scripts/backup-files.sh

# Test restore
./scripts/restore-database.sh /backups/database/knowledgevault_20260623_020000.sql.gz
```

---

## Performance Targets

### OCR Performance

| Metric | Target | Notes |
|--------|--------|-------|
| Single Page OCR | <5s | 300 DPI PDF |
| 10-page Document | <30s | Sequential processing |
| Multi-language OCR | <10s/page | Additional language models |
| Mixed Content Detection | <5s | 20-page document |

### Cache Performance

| Metric | Target | Without Cache |
|--------|--------|---------------|
| Semantic Search | <50ms | 200-500ms |
| RAG Query | <200ms | 2000-5000ms |
| Document Metadata | <10ms | 50-100ms |
| API Response | <100ms | 200-500ms |

### Admin Dashboard Performance

| Metric | Target |
|--------|--------|
| Dashboard Load | <2s |
| Stats API Response | <500ms |
| Chart Rendering | <1s |
| Real-time Updates | <100ms latency |

---

## Deployment Guide

### 1. Update Docker Compose

```yaml
# docker-compose.yml (additions)
redis:
  image: redis:7-alpine
  container_name: knowledgevault-redis
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
    - ./scripts:/scripts:ro
  command: redis-server --appendonly yes
  networks:
    - knowledgevault-network
  restart: unless-stopped

# Update ai-service environment
ai-service:
  environment:
    - REDIS_URL=redis://redis:6379/0
    - REDIS_TTL=3600
  depends_on:
    - redis

volumes:
  redis_data:
```

### 2. Build and Deploy

```bash
# Build updated images
docker-compose build ai-service backend

# Deploy with new services
docker-compose up -d redis

# Verify services
docker ps | grep redis
docker exec knowledgevault-redis redis-cli ping

# Run migrations (if any)
docker exec knowledgevault-backend mvn flyway:migrate

# Setup backup scripts
chmod +x scripts/*.sh
./scripts/setup-cron.sh
```

### 3. Install Tesseract OCR

```bash
# Already included in ai-service Dockerfile
# Verify installation
docker exec knowledgevault-ai tesseract --version
docker exec knowledgevault-ai tesseract --list-langs
```

### 4. Test Phase 6 Features

```bash
# Test OCR
curl -X POST http://localhost:8000/api/v1/ocr/process \
  -H "Authorization: Bearer YOUR_KEY" \
  -d '{"file_path": "/path/to/scanned.pdf"}'

# Test admin dashboard
curl -X GET http://localhost:8080/api/v1/admin/stats \
  -H "Authorization: Bearer YOUR_JWT"

# Test Redis cache
curl -X GET http://localhost:8000/api/v1/search/cache/stats \
  -H "Authorization: Bearer YOUR_KEY"

# Test backup scripts
./scripts/backup-database.sh
./scripts/backup-files.sh
```

---

## Documentation

### New Documentation Files

1. **docs/PHASE6_IMPLEMENTATION_SUMMARY.md** - Complete implementation summary
2. **docs/OCR_GUIDE.md** - OCR usage guide and troubleshooting
3. **docs/ADMIN_DASHBOARD_GUIDE.md** - Admin dashboard features guide
4. **docs/REDIS_CACHE_GUIDE.md** - Redis caching configuration
5. **docs/BACKUP_RESTORE_GUIDE.md** - Backup and restore procedures
6. **docs/PERFORMANCE_TUNING.md** - Performance optimization guide

---

## Success Criteria

Phase 6 is complete when:

✅ **OCR Support**
- OCR successfully processes scanned PDFs and images
- Mixed content PDFs are detected and processed correctly
- Multi-language OCR works for English, French, German, Spanish
- OCR results are integrated into document processing pipeline

✅ **Admin Dashboard**
- Admin dashboard displays comprehensive statistics
- Real-time updates work via WebSocket
- All charts and visualizations render correctly
- Performance metrics are accurate and actionable

✅ **Performance Optimization**
- Redis caching is integrated and functional
- Cache hit rate >30% for repeated queries
- Query response times improve by 4-10x with caching
- Database queries are optimized with proper indexes

✅ **Backup & Automation**
- Backup scripts run successfully
- Automated cron jobs are configured
- Restore procedures work correctly
- Monitoring alerts function as expected

✅ **Quality**
- All new features are tested and documented
- Performance targets are met
- Security measures are implemented
- System is production-ready

---

## Known Limitations

1. **OCR Performance**: Large documents (>100 pages) may take several minutes
2. **Redis Persistence**: Cache data is lost on restart (intended for ephemeral caching)
3. **Admin Permissions**: Admin dashboard requires ADMIN role (not customizable yet)
4. **Language Support**: Limited to 4 languages (eng, fre, deu, spa) by default

---

## Next Steps (Phase 7)

Phase 7 will focus on:
- Security hardening (HTTPS, rate limiting, input validation)
- Complete API documentation (OpenAPI/Swagger)
- Production deployment automation
- Monitoring and alerting setup
- Load testing and capacity planning
- User training materials
- Launch preparation

---

## Conclusion

Phase 6 adds critical advanced features that enhance the platform's capabilities and prepare it for production deployment. The combination of OCR support, comprehensive admin dashboard, Redis caching, and automated backup/monitoring scripts provides enterprise-grade reliability and performance.

**Status:** 🚀 Phase 6 Ready to Begin