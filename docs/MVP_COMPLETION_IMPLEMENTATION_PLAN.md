# KnowledgeVault AI — MVP Completion Implementation Plan

**Version:** 1.0  
**Date:** June 25, 2026  
**Status:** In Progress  
**Scope:** Complete and stabilize the Version 1.0 MVP  
**Supersedes:** Phase 7 execution sequencing until MVP exit criteria are met

---

## 1. Purpose

This plan converts the current KnowledgeVault AI repository into a deployable, testable MVP that satisfies the core product promise:

> A user can upload a document, have it indexed reliably, ask a natural-language question, receive a grounded answer with citations, and verify the answer against the original source.

The existing phase summaries describe substantial functionality, but repository review found incomplete integrations, placeholder behavior, configuration conflicts, missing MVP features, and minimal automated testing. Therefore, advanced analytics and enterprise expansion should be deferred until the core vertical slice is reliable.

---

## 2. Current State

### Implemented or partially implemented

- Spring Boot authentication and JWT security
- Users and roles
- Collections
- Document upload and local file storage
- Document metadata
- Document versions
- FastAPI document extraction and chunking
- Ollama embedding and chat clients
- PostgreSQL and pgvector models
- Semantic search and RAG endpoints
- OCR services
- Redis caching services
- Next.js authentication, document, collection, and search pages
- Docker Compose configuration
- Database backup, restore, and health-check scripts

### Launch-blocking gaps

- Database initialization order is unsafe because the vector migration runs before Flyway-created document tables.
- Document upload and version upload do not initiate indexing.
- Ingestion jobs are synchronous and stored only in application memory.
- Backend-to-AI configuration keys and default API keys are inconsistent.
- FastAPI search routes are registered with conflicting prefixes.
- Some search statistics code references invalid Python attributes.
- Version download and authenticated user attribution contain placeholder behavior.
- Frontend requests are largely hardcoded to `localhost:8080`.
- The frontend Docker API URL is not valid from a user's browser.
- Semantic and RAG frontend handlers are not connected to the rendered interface.
- Dashboard statistics and health states are simulated.
- Frontend lint fails.
- Automated tests do not cover core workflows.
- Citation responses do not satisfy the PRD's complete citation requirements.
- White-label branding and license management are not implemented.
- Audit logging does not cover all required activity.
- Development secrets and environment-specific service addresses are committed as defaults.

---

## 3. Delivery Principles

1. Complete one reliable vertical slice before adding breadth.
2. Treat phase completion as demonstrated behavior, not file existence.
3. Use durable state for business-critical and long-running work.
4. Keep the Spring Boot application as the public API and authorization boundary.
5. Keep the FastAPI service private to the application network.
6. Make citations structured, mandatory, and independently verifiable.
7. Require automated acceptance tests for every MVP capability.
8. Preserve upgrade and rollback paths for database and deployment changes.

---

## 4. Target MVP Architecture

```text
Browser
   |
   v
Next.js Frontend
   |
   v
Spring Boot API
   |----------------------|
   v                      v
PostgreSQL + pgvector   File Storage
   ^
   |
FastAPI AI Worker
   |
   v
Ollama
```

### Responsibility boundaries

| Component | Responsibilities |
|---|---|
| Next.js | Authentication UX, document management, search/chat, citations, administration |
| Spring Boot | Public REST API, authentication, authorization, business rules, audit, licensing, branding, AI coordination |
| FastAPI | Extraction, OCR, chunking, embeddings, retrieval, answer generation |
| PostgreSQL | Business data, processing jobs, chunks, embeddings, configuration, audit records |
| File storage | Original document versions and optional processed artifacts |
| Redis | Optional caching and queue support; never the sole durable record of job state |
| Ollama | Embedding and language-model inference |

---

## 5. Workstreams and Phases

### Phase 0 — Baseline and Build Reliability

**Implementation status:** Complete in code; clean database migration execution awaits a Docker-enabled validation environment.  
**Priority:** Critical  
**Estimated duration:** 3–5 working days  
**Objective:** Make a clean checkout build and start predictably.

### Tasks

#### Database and migrations

- Move `V005__create_chunks_embeddings.sql` into the backend Flyway migration sequence.
- Ensure the pgvector extension exists before vector columns are created.
- Make all migration names and ordering deterministic.
- Verify migration behavior against:
  - A new empty database
  - An existing development database
  - Upgrade and rollback procedures
- Remove schema creation paths that compete with Flyway.

#### Configuration

- Define one configuration contract for:
  - Database connection
  - AI service URL
  - Internal API key
  - Ollama URL
  - Embedding model
  - LLM model
  - Redis URL
  - File storage root
  - Browser-facing API URL
- Remove hardcoded secrets, LAN addresses, and production-like defaults.
- Add `.env.example` containing safe placeholders.
- Fail startup when required production configuration is missing.

#### Routing and dependency wiring

- Register each FastAPI router once.
- Standardize the `/api/v1` prefix.
- Use dependency injection for backend HTTP clients.
- Standardize backend property names for the AI service.
- Add explicit connection and read timeouts.

#### Build and quality gates

- Repair or regenerate the Maven wrapper.
- Make the frontend build independent of Google Fonts network access.
- Fix all frontend lint errors.
- Add commands for:
  - Backend test
  - AI service test
  - Frontend lint and build
  - Docker Compose validation
- Add CI that executes these checks on every change.

### Deliverables

- Correct Flyway migration sequence
- Unified environment configuration
- Working local build commands
- Passing frontend lint
- Initial CI pipeline
- Updated setup documentation

### Exit criteria

- A clean checkout builds without manual source edits.
- `docker compose up -d` starts the expected services.
- Database migrations complete on an empty database.
- Backend, AI service, database, Redis, and frontend health checks pass.
- No application secret or machine-specific address is required in source control.

---

### Phase 1 — Document Management Completion

**Priority:** Critical  
**Estimated duration:** 4–6 working days  
**Objective:** Make document and version management correct, secure, and complete.

### Tasks

#### Version management

- Implement actual version-file download.
- Replace hardcoded user ID fallbacks with authenticated user lookup.
- Validate that the requested version belongs to the path document.
- Make current-version updates atomic.
- Define whether deleting the current version is allowed.
- Prevent deletion of the only version unless the document is also deleted.
- Add archive and restore behavior.

#### File and transaction consistency

- Validate file size using server-side limits.
- Validate MIME type and extension consistently.
- Generate safe storage filenames instead of trusting uploaded names.
- Prevent path traversal.
- Delete newly stored files when database persistence fails.
- Define file retention behavior for soft-deleted documents.
- Add cleanup for abandoned temporary uploads.

#### Authorization

- Standardize on one Spring Security authority convention.
- Verify permissions for ADMIN, CONTRIBUTOR, and VIEWER on every endpoint.
- Ensure deleted or archived resources are not exposed unintentionally.
- Remove all anonymous or default-user fallbacks.

#### API behavior

- Add centralized exception handling.
- Define a stable API error format containing:
  - Error code
  - Message
  - HTTP status
  - Request path
  - Timestamp
  - Correlation ID
- Add pagination and stable sorting to collection and document listings.
- Validate all request DTOs.

#### Tests

- Add backend integration tests for:
  - Login and role enforcement
  - Collection CRUD
  - Document upload
  - Metadata creation and update
  - Current document download
  - New version upload
  - Historical version download
  - Set-current behavior
  - Archive and restore
  - Delete permissions
  - Invalid file handling

### Deliverables

- Complete version API
- Safe file lifecycle
- Consistent authorization
- Stable error responses
- Document-management integration test suite

### Exit criteria

- ADMIN, CONTRIBUTOR, and VIEWER acceptance tests pass.
- Every stored version can be downloaded and verified.
- No endpoint attributes actions to a fallback user.
- Database and filesystem state remain consistent after failed uploads.

---

### Phase 2 — Durable Document Ingestion

**Priority:** Critical  
**Estimated duration:** 5–8 working days  
**Objective:** Reliably convert uploaded document versions into searchable chunks and embeddings.

### Tasks

#### Processing job model

- Add a Flyway migration for durable ingestion jobs.
- Record:
  - Job ID
  - Document ID
  - Version ID
  - Status
  - Progress
  - Attempt count
  - Error code and message
  - Model names
  - Chunk and embedding counts
  - Created, started, and completed timestamps
- Define statuses: `PENDING`, `PROCESSING`, `INDEXED`, `FAILED`, and `CANCELLED`.

#### Job execution

- Trigger ingestion after initial upload.
- Trigger ingestion after new-version upload.
- Add a re-index endpoint for authorized users.
- Move processing out of the request lifecycle.
- Use a durable database-backed worker or Redis queue with PostgreSQL as the authoritative job record.
- Add retries with bounded exponential backoff.
- Add timeouts and cancellation handling.
- Recover unfinished jobs after service restart.

#### Idempotency and cleanup

- Make `(document_id, version_id)` ingestion idempotent.
- Delete or replace chunks only for the targeted version.
- Do not delete all document chunks after a version-specific failure.
- Use database transactions around chunk and embedding replacement.
- Preserve the previous searchable version until replacement indexing succeeds.

#### Extraction and OCR

- Preserve page numbers during PDF extraction.
- Detect pages with insufficient extractable text.
- Run OCR only where necessary.
- Record extraction warnings.
- Reject unsupported or encrypted documents with actionable errors.

#### Embeddings

- Validate returned embedding dimensions.
- Ensure the database vector dimension matches the configured model.
- Prevent searching across incompatible embedding models.
- Batch embedding creation.
- Record the embedding model and version for each indexed version.

### Deliverables

- Durable ingestion-job schema and API
- Background processing worker
- Upload-to-index integration
- Idempotent re-indexing
- Page-aware extraction and conditional OCR
- Ingestion tests and failure fixtures

### Exit criteria

- Upload acknowledgment completes within five seconds.
- Processing survives service restart.
- Retrying a job does not create duplicate chunks.
- Failed indexing leaves the last successful searchable version intact.
- The UI and API expose actionable processing state.

---

### Phase 3 — Search, RAG, and Mandatory Citations

**Priority:** Critical  
**Estimated duration:** 5–8 working days  
**Objective:** Deliver the core product value with verifiable answers.

### Tasks

#### Search correctness

- Fix search-statistics queries and runtime attribute errors.
- Search only successfully indexed content.
- Default to current document versions.
- Exclude deleted documents.
- Define whether archived documents are searchable.
- Add filters for:
  - Collection IDs
  - Document IDs
  - Product
  - Revision
  - Department
  - Category
  - Tags
- Enforce authorization before retrieval.

#### Hybrid search

- Validate keyword search against PostgreSQL full-text indexes.
- Validate vector search distance calculations.
- Combine keyword and vector ranks using a documented algorithm.
- Make thresholds and result limits configurable.
- Add deterministic fallback behavior when Ollama is unavailable.

#### Citation model

- Define a structured citation containing:
  - Citation ID
  - Document ID and title
  - Version ID and version number
  - Revision
  - Page number
  - Section
  - Chunk ID
  - Supporting excerpt
  - Similarity score
  - Source download/view URL
- Return citations separately from generated answer text.
- Require each answer claim to reference one or more citation IDs.
- Return an explicit insufficient-evidence response when grounding is inadequate.

#### Source verification

- Add a source endpoint that resolves a citation to the correct version.
- Support opening or downloading the cited document.
- Support page navigation for PDFs where browser capabilities allow it.
- Never cite an inaccessible, deleted, stale, or unauthorized source.

#### Quality and performance

- Build a representative evaluation dataset.
- Add expected document, page, and answer facts.
- Measure retrieval recall and citation coverage.
- Track search and generation latency.
- Tune indexes and queries against realistic data volume.

### Deliverables

- Correct semantic and hybrid search
- Structured citation API
- Grounded RAG response contract
- Source verification flow
- Retrieval and citation evaluation suite

### Exit criteria

- Citation coverage is 100% for generated grounded answers.
- Every citation resolves to the expected source version.
- Unauthorized documents never appear in retrieval or citations.
- Search meets the two-second target under the agreed test profile.
- AI responses meet the ten-second target under the agreed model and hardware profile.

---

### Phase 4 — Frontend Vertical Slice

**Priority:** High  
**Estimated duration:** 4–7 working days  
**Objective:** Expose all core workflows through a stable user interface.

### Tasks

#### API client

- Create one typed API client.
- Use the configured public API base URL everywhere.
- Remove hardcoded `localhost` references.
- Normalize authentication, JSON parsing, file downloads, and error handling.
- Add automatic handling for expired sessions.

#### Authentication and navigation

- Complete AuthContext initialization without render-loop or lint issues.
- Add role-aware navigation and action visibility.
- Protect authenticated routes.
- Make logout clear all local authentication state.

#### Document workflows

- Complete collection creation and editing.
- Complete document upload with progress.
- Display ingestion status and errors.
- Complete document details and metadata editing.
- Complete version history, upload, download, and set-current workflows.
- Add archive, restore, delete, and re-index actions based on role.

#### Search and chat

- Render traditional, semantic, hybrid, and RAG modes.
- Connect existing search handlers to visible controls.
- Display result snippets and scores where useful.
- Display structured citations adjacent to answers.
- Add source links and page indicators.
- Handle no-results and insufficient-evidence responses clearly.

#### Dashboard

- Replace simulated statistics with backend data.
- Display real service and processing state.
- Show recent uploads and failed ingestion jobs.

#### Tests

- Add component tests for core states.
- Add browser tests for:
  - Login
  - Upload
  - Processing status
  - Search
  - RAG answer
  - Citation source access
  - Version upload and download

### Deliverables

- Typed frontend API layer
- Working document-management UI
- Working semantic and RAG UI
- Citation/source viewer experience
- Real dashboard
- Browser acceptance suite

### Exit criteria

- Frontend lint and production build pass.
- Browser requests work in Docker without using internal service DNS names.
- The complete vertical slice works from a separate client machine.
- Core failure and loading states are represented in the UI.

---

### Phase 5 — Remaining MVP Product Requirements

**Priority:** High  
**Estimated duration:** 6–10 working days  
**Objective:** Close explicit Version 1.0 scope not covered by the core vertical slice.

### Tasks

#### White-label branding

- Add branding settings storage.
- Support company name, logo, primary color, secondary color, login background, and footer text.
- Add administrator branding APIs and UI.
- Apply branding without rebuilding the frontend.
- Validate uploaded branding assets.

#### License management

- Define license-key format and signature verification.
- Store license state, expiration date, edition, and feature flags.
- Add activation and status APIs.
- Enforce expiration and feature flags on the server.
- Add an administrator license-status page.
- Define offline/self-hosted activation and renewal procedures.

#### User administration

- Complete create, edit, disable, delete, and role assignment workflows.
- Prevent deletion or disabling of the last active administrator.
- Add password reset/change behavior appropriate for the MVP.

#### Audit logging

- Audit:
  - Login success and failure
  - User and role changes
  - Collection changes
  - Uploads and version changes
  - Archive, restore, and delete operations
  - Searches
  - AI questions
  - Branding and license changes
- Record actor, action, resource, outcome, timestamp, IP address, and correlation ID.
- Avoid logging passwords, tokens, document content, or sensitive prompts unnecessarily.
- Add an administrator audit-log view and filters.

#### Monitoring and usage

- Add real counts for documents, collections, versions, chunks, storage, jobs, and failures.
- Add AI usage and latency metrics.
- Expose operational health without exposing secrets.

#### Backup and recovery

- Include PostgreSQL, file storage, and configuration in backup scope.
- Document recovery point and recovery time expectations.
- Automate backup verification.
- Perform and document a full restore drill.

### Deliverables

- Runtime branding
- License enforcement
- User-management UI
- Complete audit coverage
- Real monitoring data
- Verified backup and restore process

### Exit criteria

- Every PRD Version 1.0 item has a passing acceptance test or approved documented exception.
- Branding changes are visible without a rebuild.
- Invalid or expired licenses are enforced server-side.
- Required actions appear in audit logs.
- A complete environment can be restored from backup.

---

### Phase 6 — Production Hardening and Release

**Priority:** High  
**Estimated duration:** 5–8 working days  
**Objective:** Produce an installable and supportable release candidate.

### Tasks

#### Security

- Place services behind TLS and a reverse proxy.
- Restrict CORS to configured origins.
- Keep FastAPI, PostgreSQL, Redis, and Ollama off public interfaces.
- Add rate limits for login, upload, search, and RAG.
- Add login failure controls and account lockout policy.
- Sanitize filenames, metadata, prompts, and rendered excerpts.
- Redact secrets and tokens from logs.
- Run dependency and container vulnerability scans.
- Document secret rotation.

#### Reliability and observability

- Add structured logs and correlation IDs across services.
- Add request, job, search, model, and error metrics.
- Add health, readiness, and liveness checks.
- Define alert thresholds for:
  - Failed jobs
  - Database availability
  - Storage exhaustion
  - Ollama availability
  - Search latency
  - Error rates

#### Performance

- Test representative document counts and chunk volumes.
- Validate concurrent upload, search, and chat behavior.
- Add database indexes based on measured queries.
- Validate memory and disk requirements for supported deployment sizes.
- Document hardware profiles and model-dependent latency.

#### Deployment and operations

- Pin production container versions.
- Add production Compose configuration or deployment overlays.
- Document installation, configuration, upgrade, rollback, backup, restore, and troubleshooting.
- Add release versioning and migration notes.
- Test deployment on a clean target host.

### Deliverables

- Hardened deployment configuration
- Security scan results
- Load and performance report
- Operational dashboards and alerts
- Installation and upgrade documentation
- Release candidate

### Exit criteria

- Security review has no unresolved critical or high-severity findings.
- Load tests meet the approved target profile.
- Backup and restore drills pass.
- Clean installation and upgrade tests pass.
- Release rollback has been tested.

---

## 6. Testing Strategy

### Backend

- Unit tests for business rules and validation
- Repository integration tests against PostgreSQL
- Security tests for each role
- API contract tests
- File-storage failure tests

### AI service

- Unit tests for extraction, chunking, and citation assembly
- Database integration tests for chunks and vectors
- Ollama client tests using controlled test doubles
- Ingestion restart, retry, and idempotency tests
- Retrieval quality and citation evaluation tests

### Frontend

- Component tests for forms, loading states, errors, and citations
- API client tests
- Browser tests for the complete vertical slice
- Role-specific UI tests

### Deployment

- Empty-database migration test
- Existing-database upgrade test
- Docker health test
- Backup and restore test
- Load test
- Vulnerability scan

---

## 7. MVP Acceptance Scenarios

The release candidate must pass the following scenarios:

1. An administrator creates users and assigns roles.
2. An administrator changes branding without rebuilding the application.
3. A contributor creates a collection and uploads a supported document.
4. The upload returns quickly while indexing continues in the background.
5. The user can observe indexing progress and actionable failures.
6. The indexed document appears in keyword, semantic, and hybrid search.
7. A viewer asks a question and receives an answer with structured citations.
8. Each citation opens the correct document version and identifies the page and section.
9. A contributor uploads a new version without corrupting the previous index.
10. Search defaults to the current successfully indexed version.
11. Unauthorized documents never appear in search, answers, or citations.
12. Required actions appear in the audit log.
13. An invalid or expired license is enforced according to product policy.
14. The system is backed up and restored successfully.
15. A clean self-hosted installation succeeds using documented steps.

---

## 8. Recommended Delivery Sequence

| Sequence | Milestone | Depends on |
|---:|---|---|
| 1 | Reproducible build and deployment baseline | None |
| 2 | Complete document repository | Baseline |
| 3 | Durable ingestion | Document repository |
| 4 | Search, RAG, and citations | Durable ingestion |
| 5 | Complete frontend vertical slice | Stable backend and AI contracts |
| 6 | Branding, licensing, audit, and administration | Core vertical slice |
| 7 | Production hardening and release | Completed MVP |
| 8 | Advanced Phase 7 features | Released and measured MVP |

---

## 9. Deferred Scope

The following should remain deferred until all MVP exit criteria pass:

- Collaboration and document sharing
- Comments and real-time notifications
- Email notification infrastructure
- Webhooks
- Two-factor authentication
- Advanced analytics dashboards
- Prometheus and Grafana expansion beyond minimum operational monitoring
- Multi-tenant architecture
- External identity providers
- SharePoint and network-drive connectors
- Agent workflows and knowledge graphs

These features may be valuable, but they should not displace work needed to deliver the core document-to-citation workflow.

---

## 10. Estimated Schedule

### Single developer

| Phase | Estimate |
|---|---:|
| Phase 0 | 3–5 days |
| Phase 1 | 4–6 days |
| Phase 2 | 5–8 days |
| Phase 3 | 5–8 days |
| Phase 4 | 4–7 days |
| Phase 5 | 6–10 days |
| Phase 6 | 5–8 days |
| **Total** | **32–52 working days** |

Expected elapsed duration: approximately 6–10 weeks, depending on Ollama hardware, test-data preparation, and licensing requirements.

### Parallel team

After Phase 0, backend/AI and frontend work can proceed partially in parallel. A small team can target approximately 4–6 elapsed weeks, provided API contracts are agreed before parallel implementation.

---

## 11. Definition of Done

KnowledgeVault AI Version 1.0 is complete when:

- A clean installation succeeds using documented commands.
- Core services start healthy without source changes.
- Authentication and role enforcement work consistently.
- Document and version workflows are complete.
- Upload reliably triggers durable background indexing.
- Keyword, semantic, and hybrid search operate against authorized current content.
- RAG answers are grounded and contain complete, verifiable citations.
- The source document can be opened from each citation.
- Branding and licensing are enforced at runtime.
- Required operations are audited.
- Frontend lint, builds, and acceptance tests pass.
- Backend and AI integration tests pass.
- Performance targets pass under an agreed test profile.
- Security, backup, restore, upgrade, and rollback checks pass.
- No critical path depends on placeholder behavior, simulated data, or hardcoded environment-specific values.

---

## 12. Immediate Next Actions

1. Approve this plan as the active implementation sequence.
2. Freeze the current Phase 7 enterprise-feature plan.
3. Create backlog items from Phase 0 with owners and acceptance criteria.
4. Correct migration ordering and unified configuration first.
5. Establish the automated build and test baseline.
6. Implement and demonstrate the upload-to-cited-answer vertical slice.
