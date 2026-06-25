# Phase 7: Advanced Analytics & Enterprise Features - Implementation Plan

**Date:** June 23, 2026  
**Status:** 📋 Planned  
**Duration:** 3 Weeks  
**Following:** Phase 6 (Production Readiness - Completed)

---

## Executive Summary

Phase 7 transforms KnowledgeVault into an enterprise-grade platform with advanced analytics, real-time monitoring, enhanced user experience features, and robust integration capabilities. This phase introduces comprehensive reporting, AI-powered insights, collaborative features, webhook integrations, and advanced security measures to support large-scale deployments.

### Key Objectives

1. **Advanced Analytics & Reporting** - Comprehensive dashboards, custom reports, and data visualization
2. **Real-time Monitoring & Alerting** - Prometheus metrics, Grafana dashboards, and intelligent alerts
3. **Enhanced User Experience** - Collaborative features, notifications, and personalization
4. **Integration & Extensibility** - Webhooks, API enhancements, and plugin system
5. **Enterprise Security** - Advanced authentication, audit logging, and compliance features

---

## Architecture Updates

### Phase 7 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   KnowledgeVault Enterprise                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │   Next.js 16 │  │  Spring Boot │  │   FastAPI    │        │
│  │   Frontend   │  │  Backend     │  │  AI Service  │        │
│  │   (port 3000)│  │  (port 8080) │  │  (port 8000) │        │
│  │  - Analytics │  │  - Admin API │  │  - Advanced  │        │
│  │  - Real-time │  │  - Webhooks  │  │    AI        │        │
│  │  - Collab    │  │  - Reporting │  │  - Insights  │        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘        │
│         │                 │                 │                 │
│         └─────────────────┼─────────────────┘                 │
│                           │                                   │
│  ┌────────────────────────┼────────────────────────┐        │
│  │                        │                        │        │
│  ▼                        ▼                        ▼        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ PostgreSQL   │  │    Redis     │  │   Elastic    │        │
│  │   + pgvector │  │     Cache    │  │   Search     │        │
│  │    :5432     │  │     :6379    │  │    :9200     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           Monitoring & Integration Layer              │    │
│  │  • Prometheus • Grafana • Webhooks • Alerts          │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Tasks

### Task 1: Advanced Analytics & Reporting

**Timeline:** Days 1-5  
**Priority:** High

#### 1.1 Analytics Database Schema

**File:** `database/init/V006__create_analytics.sql`

```sql
-- Search analytics table
CREATE TABLE IF NOT EXISTS search_analytics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    query_text TEXT NOT NULL,
    query_type VARCHAR(50) NOT NULL, -- 'semantic', 'keyword', 'hybrid', 'rag'
    results_count INTEGER,
    response_time_ms INTEGER,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Document access analytics
CREATE TABLE IF NOT EXISTS document_access_analytics (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT,
    action_type VARCHAR(50) NOT NULL, -- 'view', 'download', 'share', 'export'
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    referrer VARCHAR(500),
    metadata JSONB
);

-- User activity analytics
CREATE TABLE IF NOT EXISTS user_activity_analytics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    activity_data JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    session_id VARCHAR(100)
);

-- System performance metrics
CREATE TABLE IF NOT EXISTS system_metrics (
    id BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(20, 4),
    metric_unit VARCHAR(20),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    service VARCHAR(50), -- 'backend', 'ai', 'database'
    metadata JSONB
);

-- Create indexes
CREATE INDEX idx_search_analytics_user ON search_analytics(user_id, timestamp DESC);
CREATE INDEX idx_search_analytics_type ON search_analytics(query_type, timestamp DESC);
CREATE INDEX idx_search_analytics_time ON search_analytics(timestamp DESC);
CREATE INDEX idx_doc_access_doc ON document_access_analytics(document_id, timestamp DESC);
CREATE INDEX idx_doc_access_user ON document_access_analytics(user_id, timestamp DESC);
CREATE INDEX idx_user_activity_user ON user_activity_analytics(user_id, timestamp DESC);
CREATE INDEX idx_system_metrics_name ON system_metrics(metric_name, timestamp DESC);
CREATE INDEX idx_system_metrics_service ON system_metrics(service, timestamp DESC);
```

#### 1.2 Analytics Service

**File:** `backend/document-service/src/main/java/com/kva/document_service/analytics/AnalyticsService.java`

```java
@Service
@Transactional
public class AnalyticsService {
    
    @Autowired
    private SearchAnalyticsRepository searchAnalyticsRepository;
    
    @Autowired
    private DocumentAccessAnalyticsRepository documentAccessAnalyticsRepository;
    
    @Autowired
    private UserActivityAnalyticsRepository userActivityAnalyticsRepository;
    
    @Autowired
    private SystemMetricsRepository systemMetricsRepository;
    
    /**
     * Log search query
     */
    public void logSearchQuery(SearchQueryLog log) {
        SearchAnalytics analytics = new SearchAnalytics();
        analytics.setUserId(log.getUserId());
        analytics.setQueryText(log.getQueryText());
        analytics.setQueryType(log.getQueryType());
        analytics.setResultsCount(log.getResultsCount());
        analytics.setResponseTimeMs(log.getResponseTimeMs());
        analytics.setMetadata(log.getMetadata());
        
        searchAnalyticsRepository.save(analytics);
    }
    
    /**
     * Log document access
     */
    public void logDocumentAccess(DocumentAccessLog log) {
        DocumentAccessAnalytics analytics = new DocumentAccessAnalytics();
        analytics.setDocumentId(log.getDocumentId());
        analytics.setUserId(log.getUserId());
        analytics.setActionType(log.getActionType());
        analytics.setReferrer(log.getReferrer());
        analytics.setMetadata(log.getMetadata());
        
        documentAccessAnalyticsRepository.save(analytics);
    }
    
    /**
     * Log user activity
     */
    public void logUserActivity(UserActivityLog log) {
        UserActivityAnalytics analytics = new UserActivityAnalytics();
        analytics.setUserId(log.getUserId());
        analytics.setActivityType(log.getActivityType());
        analytics.setActivityData(log.getActivityData());
        analytics.setSessionId(log.getSessionId());
        
        userActivityAnalyticsRepository.save(analytics);
    }
    
    /**
     * Record system metric
     */
    public void recordSystemMetric(SystemMetric metric) {
        SystemMetrics metrics = new SystemMetrics();
        metrics.setMetricName(metric.getMetricName());
        metrics.setMetricValue(metric.getMetricValue());
        metrics.setMetricUnit(metric.getMetricUnit());
        metrics.setService(metric.getService());
        metrics.setMetadata(metric.getMetadata());
        
        systemMetricsRepository.save(metrics);
    }
    
    /**
     * Get search analytics report
     */
    public SearchAnalyticsReport getSearchAnalyticsReport(
        LocalDateTime startDate, 
        LocalDateTime endDate,
        String userId
    ) {
        List<SearchAnalytics> analytics = searchAnalyticsRepository
            .findByTimestampBetweenAndUserId(
                startDate, 
                endDate, 
                userId
            );
        
        SearchAnalyticsReport report = new SearchAnalyticsReport();
        report.setTotalQueries(analytics.size());
        report.setQueriesByType(analytics.stream()
            .collect(Collectors.groupingBy(SearchAnalytics::getQueryType, Collectors.counting())));
        report.setAvgResponseTimeMs(analytics.stream()
            .mapToInt(SearchAnalytics::getResponseTimeMs)
            .average()
            .orElse(0.0));
        report.setAvgResultsCount(analytics.stream()
            .mapToInt(SearchAnalytics::getResultsCount)
            .average()
            .orElse(0.0));
        
        return report;
    }
    
    /**
     * Get popular documents
     */
    public List<PopularDocument> getPopularDocuments(
        LocalDateTime startDate,
        LocalDateTime endDate,
        int limit
    ) {
        return documentAccessAnalyticsRepository
            .findPopularDocuments(startDate, endDate, limit);
    }
    
    /**
     * Get user activity heatmap
     */
    public UserActivityHeatmap getUserActivityHeatmap(
        LocalDateTime startDate,
        LocalDateTime endDate,
        String userId
    ) {
        List<UserActivityAnalytics> activities = userActivityAnalyticsRepository
            .findByUserIdAndTimestampBetweenOrderByTimestamp(userId, startDate, endDate);
        
        // Group by hour of day and day of week
        Map<String, Long> heatmap = activities.stream()
            .collect(Collectors.groupingBy(
                activity -> {
                    LocalDateTime timestamp = activity.getTimestamp();
                    return String.format("%d-%d", 
                        timestamp.getDayOfWeek().getValue(), 
                        timestamp.getHour());
                },
                Collectors.counting()
            ));
        
        UserActivityHeatmap result = new UserActivityHeatmap();
        result.setUserId(userId);
        result.setStartDate(startDate);
        result.setEndDate(endDate);
        result.setHeatmapData(heatmap);
        
        return result;
    }
}
```

#### 1.3 Analytics Controller

**File:** `backend/document-service/src/main/java/com/kva/document_service/analytics/AnalyticsController.java`

```java
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    
    @Autowired
    private AnalyticsService analyticsService;
    
    /**
     * Get search analytics report
     */
    @GetMapping("/search")
    public ResponseEntity<SearchAnalyticsReport> getSearchAnalytics(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
        @RequestParam(required = false) String userId
    ) {
        SearchAnalyticsReport report = analyticsService.getSearchAnalyticsReport(
            startDate, endDate, userId
        );
        return ResponseEntity.ok(report);
    }
    
    /**
     * Get popular documents
     */
    @GetMapping("/documents/popular")
    public ResponseEntity<List<PopularDocument>> getPopularDocuments(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
        @RequestParam(defaultValue = "10") int limit
    ) {
        List<PopularDocument> documents = analyticsService.getPopularDocuments(
            startDate, endDate, limit
        );
        return ResponseEntity.ok(documents);
    }
    
    /**
     * Get user activity heatmap
     */
    @GetMapping("/users/activity/heatmap")
    public ResponseEntity<UserActivityHeatmap> getUserActivityHeatmap(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
        @RequestParam String userId
    ) {
        UserActivityHeatmap heatmap = analyticsService.getUserActivityHeatmap(
            startDate, endDate, userId
        );
        return ResponseEntity.ok(heatmap);
    }
    
    /**
     * Get system performance metrics
     */
    @GetMapping("/system/performance")
    public ResponseEntity<SystemPerformanceReport> getSystemPerformance(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        SystemPerformanceReport report = analyticsService.getSystemPerformanceReport(
            startDate, endDate
        );
        return ResponseEntity.ok(report);
    }
    
    /**
     * Generate custom report
     */
    @PostMapping("/reports/generate")
    public ResponseEntity<ReportGenerationResponse> generateCustomReport(
        @RequestBody CustomReportRequest request
    ) {
        ReportGenerationResponse response = analyticsService.generateCustomReport(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Export report
     */
    @GetMapping("/reports/{reportId}/export")
    public ResponseEntity<byte[]> exportReport(
        @PathVariable Long reportId,
        @RequestParam(defaultValue = "csv") String format
    ) {
        byte[] reportData = analyticsService.exportReport(reportId, format);
        
        return ResponseEntity.ok()
            .header("Content-Disposition", 
                "attachment; filename=report_" + reportId + "." + format)
            .body(reportData);
    }
}
```

#### 1.4 Frontend Analytics Dashboard

**File:** `frontend/knowledgevault-ui/app/analytics/page.tsx`

```typescript
"use client";

import { useState, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { LineChart, BarChart, HeatmapChart } from '@/components/charts';

export default function AnalyticsDashboard() {
    const [searchData, setSearchData] = useState(null);
    const [popularDocs, setPopularDocs] = useState([]);
    const [userActivity, setUserActivity] = useState(null);
    const [timeRange, setTimeRange] = useState('7d');
    
    useEffect(() => {
        fetchAnalyticsData();
    }, [timeRange]);
    
    const fetchAnalyticsData = async () => {
        const [searchRes, docsRes, activityRes] = await Promise.all([
            fetch(`/api/v1/analytics/search?startDate=${getStartDate()}&endDate=${getEndDate()}`),
            fetch(`/api/v1/analytics/documents/popular?startDate=${getStartDate()}&endDate=${getEndDate()}`),
            fetch(`/api/v1/analytics/users/activity/heatmap?userId=${getCurrentUserId()}&startDate=${getStartDate()}&endDate=${getEndDate()}`)
        ]);
        
        setSearchData(await searchRes.json());
        setPopularDocs(await docsRes.json());
        setUserActivity(await activityRes.json());
    };
    
    return (
        <div className="container mx-auto p-6">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-3xl font-bold">Analytics Dashboard</h1>
                <select 
                    value={timeRange} 
                    onChange={(e) => setTimeRange(e.target.value)}
                    className="border rounded px-4 py-2"
                >
                    <option value="24h">Last 24 Hours</option>
                    <option value="7d">Last 7 Days</option>
                    <option value="30d">Last 30 Days</option>
                    <option value="90d">Last 90 Days</option>
                </select>
            </div>
            
            {/* Overview Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                <Card>
                    <h3 className="text-sm text-gray-500">Total Searches</h3>
                    <p className="text-2xl font-bold">{searchData?.totalQueries || 0}</p>
                </Card>
                <Card>
                    <h3 className="text-sm text-gray-500">Avg Response Time</h3>
                    <p className="text-2xl font-bold">{searchData?.avgResponseTimeMs?.toFixed(0) || 0}ms</p>
                </Card>
                <Card>
                    <h3 className="text-sm text-gray-500">Avg Results</h3>
                    <p className="text-2xl font-bold">{searchData?.avgResultsCount?.toFixed(1) || 0}</p>
                </Card>
                <Card>
                    <h3 className="text-sm text-gray-500">Active Users</h3>
                    <p className="text-2xl font-bold">{userActivity?.uniqueUsers || 0}</p>
                </Card>
            </div>
            
            {/* Search Analytics Chart */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
                <Card>
                    <h3 className="text-lg font-semibold mb-4">Search Volume Over Time</h3>
                    <LineChart data={searchData?.queryVolumeChart || []} />
                </Card>
                
                <Card>
                    <h3 className="text-lg font-semibold mb-4">Query Type Distribution</h3>
                    <BarChart data={searchData?.queriesByType || {}} />
                </Card>
            </div>
            
            {/* Popular Documents */}
            <Card className="mb-6">
                <h3 className="text-lg font-semibold mb-4">Popular Documents</h3>
                <div className="space-y-2">
                    {popularDocs.map((doc, index) => (
                        <div key={doc.id} className="flex justify-between items-center p-3 border rounded">
                            <div>
                                <p className="font-medium">{doc.title}</p>
                                <p className="text-sm text-gray-500">{doc.collectionName}</p>
                            </div>
                            <div className="text-right">
                                <p className="font-semibold">{doc.accessCount} views</p>
                                <p className="text-sm text-gray-500">#{index + 1}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </Card>
            
            {/* User Activity Heatmap */}
            <Card>
                <h3 className="text-lg font-semibold mb-4">User Activity Heatmap</h3>
                <HeatmapChart data={userActivity?.heatmapData || {}} />
            </Card>
        </div>
    );
}
```

---

### Task 2: Real-time Monitoring & Alerting

**Timeline:** Days 6-10  
**Priority:** High

#### 2.1 Prometheus Metrics Exporter

**File:** `backend/document-service/src/main/java/com/kva/document_service/monitoring/PrometheusMetricsExporter.java`

```java
@Component
public class PrometheusMetricsExporter {
    
    private final Counter searchRequestsTotal;
    private final Histogram searchResponseTime;
    private final Gauge activeConnections;
    private final Counter documentUploadsTotal;
    private final Counter errorsTotal;
    
    public PrometheusMetricsExporter(MeterRegistry registry) {
        this.searchRequestsTotal = Counter.builder("knowledgevault_search_requests_total")
            .description("Total number of search requests")
            .tag("type", "all")
            .register(registry);
        
        this.searchResponseTime = Histogram.builder("knowledgevault_search_response_time_seconds")
            .description("Search response time in seconds")
            .tag("type", "all")
            .register(registry);
        
        this.activeConnections = Gauge.builder("knowledgevault_active_connections", 
            () -> getActiveConnectionsCount())
            .description("Number of active database connections")
            .register(registry);
        
        this.documentUploadsTotal = Counter.builder("knowledgevault_document_uploads_total")
            .description("Total number of document uploads")
            .register(registry);
        
        this.errorsTotal = Counter.builder("knowledgevault_errors_total")
            .description("Total number of errors")
            .tag("service", "backend")
            .register(registry);
    }
    
    public void recordSearchRequest(String queryType) {
        searchRequestsTotal.increment();
        Counter.builder("knowledgevault_search_requests_total")
            .tag("type", queryType)
            .register(Metrics.globalRegistry)
            .increment();
    }
    
    public void recordSearchResponseTime(double durationSeconds, String queryType) {
        searchResponseTime.record(durationSeconds);
        Histogram.builder("knowledgevault_search_response_time_seconds")
            .tag("type", queryType)
            .register(Metrics.globalRegistry)
            .record(durationSeconds);
    }
    
    public void recordDocumentUpload(String documentType) {
        documentUploadsTotal.increment();
        Counter.builder("knowledgevault_document_uploads_total")
            .tag("type", documentType)
            .register(Metrics.globalRegistry)
            .increment();
    }
    
    public void recordError(String errorType, String service) {
        errorsTotal.increment();
        Counter.builder("knowledgevault_errors_total")
            .tag("type", errorType)
            .tag("service", service)
            .register(Metrics.globalRegistry)
            .increment();
    }
    
    private int getActiveConnectionsCount() {
        // Get active connections from data source
        return 0; // Implementation depends on your data source
    }
}
```

#### 2.2 Alert Manager

**File:** `backend/document-service/src/main/java/com/kva/document_service/monitoring/AlertManager.java`

```java
@Service
public class AlertManager {
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private MetricsRepository metricsRepository;
    
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    
    /**
     * Register alert rule
     */
    public void registerAlertRule(AlertRule rule) {
        alertRules.put(rule.getId(), rule);
    }
    
    /**
     * Check alert conditions
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkAlerts() {
        for (AlertRule rule : alertRules.values()) {
            if (shouldTriggerAlert(rule)) {
                triggerAlert(rule);
            }
        }
    }
    
    private boolean shouldTriggerAlert(AlertRule rule) {
        switch (rule.getConditionType()) {
            case "threshold":
                return checkThreshold(rule);
            case "rate":
                return checkRate(rule);
            case "anomaly":
                return checkAnomaly(rule);
            default:
                return false;
        }
    }
    
    private boolean checkThreshold(AlertRule rule) {
        double currentValue = metricsRepository.getLatestMetricValue(rule.getMetricName());
        double threshold = Double.parseDouble(rule.getThreshold());
        
        switch (rule.getOperator()) {
            case "greater_than":
                return currentValue > threshold;
            case "less_than":
                return currentValue < threshold;
            case "equals":
                return currentValue == threshold;
            default:
                return false;
        }
    }
    
    private boolean checkRate(AlertRule rule) {
        double currentRate = metricsRepository.getRate(rule.getMetricName(), rule.getTimeWindow());
        double threshold = Double.parseDouble(rule.getThreshold());
        return currentRate > threshold;
    }
    
    private boolean checkAnomaly(AlertRule rule) {
        // Implement anomaly detection using statistical methods
        double currentValue = metricsRepository.getLatestMetricValue(rule.getMetricName());
        double mean = metricsRepository.getMean(rule.getMetricName(), rule.getTimeWindow());
        double stdDev = metricsRepository.getStdDev(rule.getMetricName(), rule.getTimeWindow());
        
        // Trigger if value is more than 2 standard deviations from mean
        return Math.abs(currentValue - mean) > 2 * stdDev;
    }
    
    private void triggerAlert(AlertRule rule) {
        Alert alert = Alert.builder()
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .severity(rule.getSeverity())
            .message(generateAlertMessage(rule))
            .timestamp(LocalDateTime.now())
            .build();
        
        // Send notifications
        notificationService.sendAlert(alert, rule.getNotificationChannels());
        
        // Log alert
        logAlert(alert);
    }
    
    private String generateAlertMessage(AlertRule rule) {
        double currentValue = metricsRepository.getLatestMetricValue(rule.getMetricName());
        return String.format("Alert: %s. Current value: %.2f, Threshold: %s %s",
            rule.getName(),
            currentValue,
            rule.getOperator(),
            rule.getThreshold()
        );
    }
    
    private void logAlert(Alert alert) {
        // Log alert to database and monitoring system
    }
}
```

#### 2.3 Grafana Dashboard Configuration

**File:** `monitoring/grafana/dashboards/knowledgevault.json`

```json
{
  "dashboard": {
    "title": "KnowledgeVault Overview",
    "panels": [
      {
        "title": "Search Requests",
        "targets": [
          {
            "expr": "rate(knowledgevault_search_requests_total[5m])",
            "legendFormat": "{{type}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Search Response Time",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(knowledgevault_search_response_time_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Active Connections",
        "targets": [
          {
            "expr": "knowledgevault_active_connections",
            "legendFormat": "Connections"
          }
        ],
        "type": "stat"
      },
      {
        "title": "Document Uploads",
        "targets": [
          {
            "expr": "rate(knowledgevault_document_uploads_total[1h])",
            "legendFormat": "Uploads/hour"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(knowledgevault_errors_total[5m])",
            "legendFormat": "{{type}} - {{service}}"
          }
        ],
        "type": "graph"
      }
    ],
    "refresh": "30s"
  }
}
```

#### 2.4 Docker Compose Updates

**File:** `docker-compose.yml` (additions)

```yaml
  # Prometheus for metrics collection
  prometheus:
    image: prom/prometheus:latest
    container_name: knowledgevault-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
    networks:
      - knowledgevault-network
    restart: unless-stopped

  # Grafana for visualization
  grafana:
    image: grafana/grafana:latest
    container_name: knowledgevault-grafana
    ports:
      - "3001:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin123
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards
    depends_on:
      - prometheus
    networks:
      - knowledgevault-network
    restart: unless-stopped

volumes:
  prometheus_data:
  grafana_data:
```

---

### Task 3: Enhanced User Experience

**Timeline:** Days 11-14  
**Priority:** Medium

#### 3.1 Notification Service

**File:** `backend/document-service/src/main/java/com/kva/document_service/notifications/NotificationService.java`

```java
@Service
public class NotificationService {
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private WebSocketService webSocketService;
    
    /**
     * Send notification to user
     */
    public void sendNotification(Notification notification) {
        // Save to database
        notificationRepository.save(notification);
        
        // Send via WebSocket for real-time delivery
        webSocketService.sendToUser(notification.getUserId(), notification);
        
        // Send email if enabled
        if (notification.isEmailEnabled()) {
            emailService.sendNotificationEmail(notification);
        }
    }
    
    /**
     * Broadcast notification to all users
     */
    public void broadcastNotification(Notification notification) {
        // Save to database for all users
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Notification userNotification = new Notification(notification);
            userNotification.setUserId(user.getId());
            notificationRepository.save(userNotification);
            
            // Send via WebSocket
            webSocketService.sendToUser(user.getId(), userNotification);
        }
    }
    
    /**
     * Get user notifications
     */
    public List<Notification> getUserNotifications(Long userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }
    
    /**
     * Mark notification as read
     */
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
    
    /**
     * Send alert notification
     */
    public void sendAlert(Alert alert, List<String> channels) {
        Notification notification = Notification.builder()
            .type("ALERT")
            .title("System Alert: " + alert.getRuleName())
            .message(alert.getMessage())
            .severity(alert.getSeverity())
            .timestamp(LocalDateTime.now())
            .build();
        
        if (channels.contains("email")) {
            notification.setEmailEnabled(true);
        }
        
        // Send to admin users
        List<User> adminUsers = userRepository.findByRole("ADMIN");
        for (User admin : adminUsers) {
            Notification adminNotification = new Notification(notification);
            adminNotification.setUserId(admin.getId());
            sendNotification(adminNotification);
        }
    }
}
```

#### 3.2 Collaboration Features

**File:** `backend/document-service/src/main/java/com/kva/document_service/collaboration/CollaborationService.java`

```java
@Service
public class CollaborationService {
    
    @Autowired
    private DocumentShareRepository documentShareRepository;
    
    @Autowired
    private CommentRepository commentRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * Share document with user
     */
    public DocumentShare shareDocument(Long documentId, Long recipientId, Long senderId, SharePermission permission) {
        DocumentShare share = DocumentShare.builder()
            .documentId(documentId)
            .senderId(senderId)
            .recipientId(recipientId)
            .permission(permission)
            .sharedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
        
        documentShareRepository.save(share);
        
        // Send notification to recipient
        Notification notification = Notification.builder()
            .type("DOCUMENT_SHARED")
            .title("Document Shared With You")
            .message("A document has been shared with you")
            .recipientId(recipientId)
            .timestamp(LocalDateTime.now())
            .build();
        
        notificationService.sendNotification(notification);
        
        return share;
    }
    
    /**
     * Add comment to document
     */
    public Comment addComment(Long documentId, Long userId, String content) {
        Comment comment = Comment.builder()
            .documentId(documentId)
            .userId(userId)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();
        
        commentRepository.save(comment);
        
        // Notify document owner and other collaborators
        notifyCollaborators(documentId, userId, "New comment added");
        
        return comment;
    }
    
    /**
     * Get document comments
     */
    public List<Comment> getDocumentComments(Long documentId) {
        return commentRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
    }
    
    /**
     * Get shared documents for user
     */
    public List<DocumentShare> getSharedDocuments(Long userId) {
        return documentShareRepository.findByRecipientIdOrderBySharedAtDesc(userId);
    }
    
    private void notifyCollaborators(Long documentId, Long senderId, String message) {
        List<DocumentShare> shares = documentShareRepository.findByDocumentId(documentId);
        
        for (DocumentShare share : shares) {
            if (!share.getRecipientId().equals(senderId)) {
                Notification notification = Notification.builder()
                    .type("COLLABORATION")
                    .title("Document Update")
                    .message(message)
                    .recipientId(share.getRecipientId())
                    .timestamp(LocalDateTime.now())
                    .build();
                
                notificationService.sendNotification(notification);
            }
        }
    }
}
```

---

### Task 4: Integration & Extensibility

**Timeline:** Days 15-18  
**Priority:** Medium

#### 4.1 Webhook Service

**File:** `backend/document-service/src/main/java/com/kva/document_service/webhook/WebhookService.java`

```java
@Service
public class WebhookService {
    
    @Autowired
    private WebhookRepository webhookRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * Register webhook
     */
    public Webhook registerWebhook(Webhook webhook) {
        webhook.setSecret(generateSecret());
        webhook.setCreatedAt(LocalDateTime.now());
        webhook.setEnabled(true);
        return webhookRepository.save(webhook);
    }
    
    /**
     * Trigger webhook
     */
    public void triggerWebhook(String eventType, Object payload) {
        List<Webhook> webhooks = webhookRepository.findByEventTypeAndEnabled(eventType, true);
        
        for (Webhook webhook : webhooks) {
            executorService.submit(() -> {
                try {
                    WebhookPayload webhookPayload = WebhookPayload.builder()
                        .event(eventType)
                        .timestamp(Instant.now().toString())
                        .payload(payload)
                        .build();
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-KnowledgeVault-Signature", 
                        generateSignature(webhookPayload, webhook.getSecret()));
                    headers.set("X-KnowledgeVault-Event", eventType);
                    
                    HttpEntity<WebhookPayload> request = new HttpEntity<>(webhookPayload, headers);
                    
                    ResponseEntity<String> response = restTemplate.postForEntity(
                        webhook.getUrl(),
                        request,
                        String.class
                    );
                    
                    logWebhookDelivery(webhook, response.getStatusCodeValue());
                    
                } catch (Exception e) {
                    log.error("Failed to deliver webhook", e);
                    logWebhookDelivery(webhook, null);
                }
            });
        }
    }
    
    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private String generateSignature(WebhookPayload payload, String secret) {
        // Generate HMAC-SHA256 signature
        // Implementation depends on your security requirements
        return "signature";
    }
    
    private void logWebhookDelivery(Webhook webhook, Integer statusCode) {
        // Log webhook delivery status
    }
}
```

#### 4.2 API Rate Limiting

**File:** `backend/document-service/src/main/java/com/kva/document_service/security/RateLimiter.java`

```java
@Component
public class RateLimiter {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private final Map<String, RateLimitConfig> rateLimitConfigs = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Default rate limits
        rateLimitConfigs.put("search", new RateLimitConfig(30, Duration.ofMinutes(1)));
        rateLimitConfigs.put("api", new RateLimitConfig(100, Duration.ofMinutes(1)));
        rateLimitConfigs.put("upload", new RateLimitConfig(10, Duration.ofMinutes(1)));
    }
    
    /**
     * Check if request is rate limited
     */
    public boolean isRateLimited(String clientId, String endpoint) {
        RateLimitConfig config = getConfigForEndpoint(endpoint);
        String key = "ratelimit:" + endpoint + ":" + clientId;
        
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count != null && count == 1) {
            redisTemplate.expire(key, config.getTimeWindow());
        }
        
        return count != null && count > config.getMaxRequests();
    }
    
    /**
     * Get remaining requests
     */
    public int getRemainingRequests(String clientId, String endpoint) {
        RateLimitConfig config = getConfigForEndpoint(endpoint);
        String key = "ratelimit:" + endpoint + ":" + clientId;
        
        Long count = redisTemplate.opsForValue().get(key);
        if (count == null) {
            return config.getMaxRequests();
        }
        
        return Math.max(0, config.getMaxRequests() - count.intValue());
    }
    
    private RateLimitConfig getConfigForEndpoint(String endpoint) {
        if (endpoint.contains("/search")) {
            return rateLimitConfigs.get("search");
        } else if (endpoint.contains("/upload")) {
            return rateLimitConfigs.get("upload");
        } else {
            return rateLimitConfigs.get("api");
        }
    }
}
```

---

### Task 5: Enterprise Security

**Timeline:** Days 19-21  
**Priority:** High

#### 5.1 Audit Logging

**File:** `backend/document-service/src/main/java/com/kva/document_service/audit/AuditLogger.java`

```java
@Aspect
@Component
public class AuditLogger {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    /**
     * Audit log for controller methods
     */
    @Around("@annotation(auditable)")
    public Object auditControllerMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        
        AuditLog auditLog = AuditLog.builder()
            .userId(getCurrentUserId())
            .action(auditable.action())
            .resource(auditable.resource())
            .method(joinPoint.getSignature().getName())
            .ipAddress(request.getRemoteAddr())
            .userAgent(request.getHeader("User-Agent"))
            .timestamp(LocalDateTime.now())
            .build();
        
        try {
            Object result = joinPoint.proceed();
            auditLog.setStatus("SUCCESS");
            return result;
        } catch (Exception e) {
            auditLog.setStatus("FAILED");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            auditLogRepository.save(auditLog);
        }
    }
    
    /**
     * Get audit logs
     */
    public List<AuditLog> getAuditLogs(LocalDateTime startDate, LocalDateTime endDate, Long userId) {
        if (userId != null) {
            return auditLogRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(
                userId, startDate, endDate
            );
        } else {
            return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(
                startDate, endDate
            );
        }
    }
}
```

#### 5.2 Two-Factor Authentication

**File:** `backend/document-service/src/main/java/com/kva/document_service/security/TwoFactorAuthService.java`

```java
@Service
public class TwoFactorAuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EmailService emailService;
    
    /**
     * Enable 2FA for user
     */
    public String enableTwoFactor(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        String secret = generateSecret();
        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(false); // Requires verification
        userRepository.save(user);
        
        return secret;
    }
    
    /**
     * Verify and enable 2FA
     */
    public boolean verifyAndEnableTwoFactor(Long userId, String code) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (verifyCode(user.getTwoFactorSecret(), code)) {
            user.setTwoFactorEnabled(true);
            userRepository.save(user);
            return true;
        }
        
        return false;
    }
    
    /**
     * Send 2FA code via email
     */
    public void sendTwoFactorCode(String email) {
        String code = generateCode();
        // Store code in Redis with expiration
        redisTemplate.opsForValue().set(
            "2fa:" + email,
            code,
            Duration.ofMinutes(5)
        );
        
        emailService.sendTwoFactorCode(email, code);
    }
    
    /**
     * Verify 2FA code
     */
    public boolean verifyTwoFactorCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get("2fa:" + email);
        return code.equals(storedCode);
    }
    
    private String generateSecret() {
        // Generate 2FA secret
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private String generateCode() {
        // Generate 6-digit code
        return String.format("%06d", new Random().nextInt(1000000));
    }
    
    private boolean verifyCode(String secret, String code) {
        // Verify TOTP code
        // Implementation depends on TOTP library
        return true;
    }
}
```

---

## Deployment Guide

### 1. Update Docker Compose

```yaml
# Add new services to docker-compose.yml
prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    - prometheus_data:/prometheus
  networks:
    - knowledgevault-network

grafana:
  image: grafana/grafana:latest
  ports:
    - "3001:3000"
  environment:
    GF_SECURITY_ADMIN_USER: admin
    GF_SECURITY_ADMIN_PASSWORD: admin123
  volumes:
    - grafana_data:/var/lib/grafana
    - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards
  depends_on:
    - prometheus
  networks:
    - knowledgevault-network
```

### 2. Run Database Migrations

```bash
docker exec knowledgevault-db psql -U postgres -d knowledgevault -f /docker-entrypoint-initdb.d/V006__create_analytics.sql
```

### 3. Deploy Services

```bash
# Build updated images
docker-compose build backend ai-service

# Start monitoring services
docker-compose up -d prometheus grafana

# Restart backend and AI service
docker-compose up -d backend ai-service

# Verify services
docker ps
```

### 4. Configure Grafana

1. Access Grafana at http://localhost:3001
2. Login with admin/admin123
3. Add Prometheus data source
4. Import KnowledgeVault dashboard

---

## Performance Targets

### Analytics Performance

| Metric | Target | Notes |
|--------|--------|-------|
| Report Generation | <5s | For 30-day reports |
| Real-time Analytics | <100ms | For dashboard updates |
| Data Export | <30s | For CSV export of 10K records |

### Monitoring Performance

| Metric | Target | Notes |
|--------|--------|-------|
| Metrics Collection | <10ms | Per metric |
| Alert Detection | <1s | Per rule check |
| Notification Delivery | <5s | Email + WebSocket |

### User Experience

| Metric | Target | Notes |
|--------|--------|-------|
| Page Load Time | <2s | Analytics dashboard |
| Real-time Updates | <100ms | WebSocket latency |
| Webhook Delivery | <3s | Success rate >99% |

---

## Testing Strategy

### Analytics Testing

```bash
# Test search analytics
curl -X POST http://localhost:8080/api/v1/search/semantic \
  -H "Authorization: Bearer YOUR_JWT" \
  -d '{"query": "test query"}'

# Get analytics report
curl -X GET "http://localhost:8080/api/v1/analytics/search?startDate=2026-06-01T00:00:00&endDate=2026-06-30T23:59:59" \
  -H "Authorization: Bearer YOUR_JWT"
```

### Monitoring Testing

```bash
# Test Prometheus metrics
curl http://localhost:9090/api/v1/query?query=knowledgevault_search_requests_total

# Test alerting
curl -X POST http://localhost:8080/api/v1/admin/alerts/test \
  -H "Authorization: Bearer YOUR_JWT"
```

### Integration Testing

```bash
# Register webhook
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/webhook",
    "event_type": "document.uploaded",
    "secret": "test_secret"
  }'

# Test rate limiting
for i in {1..40}; do
  curl -X GET http://localhost:8080/api/v1/search \
    -H "Authorization: Bearer YOUR_JWT"
done
```

---

## Future Enhancements

### Phase 8 Potential Features
1. **Advanced AI Models** - GPT-4 integration, fine-tuned models
2. **Natural Language Processing** - Advanced NER, sentiment analysis
3. **Multi-tenant Architecture** - Organization isolation, SSO
4. **Advanced Security** - SIEM integration, threat detection
5. **Performance Optimization** - Elasticsearch, distributed processing

---

## Conclusion

Phase 7 transforms KnowledgeVault into an enterprise-grade platform with advanced analytics, real-time monitoring, enhanced user experience, and robust integration capabilities. The system now provides comprehensive insights, proactive monitoring, collaborative features, and enterprise-grade security suitable for large-scale deployments.

**Estimated Duration:** 3 weeks  
**Complexity:** High  
**Impact:** Transformative