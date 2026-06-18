# KnowledgeVault AI — Implementation Plan

Version: 1.0
Date: June 2026
Author: Engineering
Status: Approved for Execution

> **Source of truth:** This plan implements the product requirements defined in `docs/knowledgeVault_prd.md`. Where `fastapiPrd.md`, `documentSB.prd`, or `directorystructure.md` add detail, they are treated as supplementary. Where they conflict, the main PRD wins.

---

## 1. Current State Assessment

| Component | Status | Notes |
|-----------|--------|-------|
| `frontend/knowledgevault-ui` | Scaffolded | Next.js 16.2.9 + React 19.2.4 + Tailwind 4. Default starter page only. |
| `backend/document-service` | Scaffolded | Spring Boot 4.1.0, Java 17, Flyway, JDBC, Security, WebMVC. Empty app class only. |
| `ai-service` | Empty | FastAPI service to be created from scratch. |
| `database/` | Not created | Flyway migrations directory exists but is empty. |
| Docker Compose | Partial | `backend/document-service/compose.yaml` has Postgres only. No root orchestration. |
| CI/CD | None | No pipelines defined. |

### Technology Decisions (Locked)

- **Frontend:** Next.js **16.2.9** (App Router), React **19.2.4**, TypeScript **5**, Tailwind **4**.
- **Backend:** Spring Boot **4.1.0**, Java **17**, Spring Security, Spring Data JDBC (note: PRD says JPA; we use Flyway-managed DDL + Spring Data JDBC/JdbcTemplate for the stateless business layer — decision documented in §6).
- **AI Service:** **FastAPI** (Python 3.12), async, stateless.
- **Database:** PostgreSQL **16** + **pgvector** extension.
- **LLM Runtime:** **Ollama** (default), models: Qwen, Llama, Gemma, DeepSeek. Embeddings: `nomic-embed-text`, `bge-m3`, `mxbai-embed-large`.
- **Deployment:** Docker Compose (single-host, self-hosted primary model).

> ⚠️ **Next.js 16 Note:** Per `AGENTS.md`, Next.js 16 has breaking changes vs. training knowledge. Before writing any frontend code, consult `node_modules/next/dist/docs/`. Do not rely on memorized Next.js 13/14/15 conventions.

---

## 2. Architecture Overview

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Next.js    │────▶│  Spring Boot 4.1 │────▶│     FastAPI     │
│  Frontend    │ REST│  document-service│ REST│    ai-service   │
│  (port 3000) │◀────│   (port 8080)    │◀────│   (port 8000)   │
└──────────────┘     └────────┬─────────┘     └────────┬────────┘
                              │ JDBC                   │ asyncpg/SQLAlchemy
                              ▼                        ▼
                     ┌──────────────────────────────────┐
                     │   PostgreSQL 16 + pgvector       │
                     │      (port 5432)                 │
                     └──────────────────────────────────┘
                              ▲                        ▲
                              │                        │
                     ┌────────┴─────────┐    ┌─────────┴────────┐
                     │   Object Storage │    │      Ollama      │
                     │  (docs: originals│    │   (port 11434)   │
                     │   processed,     │    │  LLM + Embeddings│
                     │   archived)      │    └──────────────────┘
                     └──────────────────┘
```

### Responsibility Boundaries (from PRD §"Product Architecture")

| Layer | Owns | Does NOT Own |
|-------|------|--------------|
| **Spring Boot** | AuthN/AuthZ, Users, Roles, Document *metadata*, Collections, Audit, Licensing, Branding, System Config, REST gateway to frontend | Vector operations, LLM calls, text extraction |
| **FastAPI** | Document processing (extract/chunk/embed), semantic + hybrid search, RAG pipeline, LLM orchestration | User identity, business records of truth |
| **PostgreSQL** | Single shared instance; Spring Boot owns business tables, FastAPI owns `document_chunks` + `embeddings` (via pgvector) | — |
| **Next.js** | UI, SSR/CSR rendering, auth token handling, API calls to Spring Boot | Direct DB access, direct FastAPI calls (all proxied through Spring Boot) |

> **Key principle (per `directorystructure.md`):** Document metadata lives in Spring Boot; AI processing is stateless in FastAPI. This enables swapping the AI stack without touching customer business data.

---

## 3. Data Model

### 3.1 Database Schema (Flyway Migrations)

Migrations live in `backend/document-service/src/main/resources/db/migration/`. FastAPI-specific tables (`document_chunks`, `embeddings`) are also created here so the schema is owned in one place, but FastAPI connects with its own credentials (read/write to its tables only).

```
V001__create_users_roles.sql
    users (id, username, email, password_hash, status, created_at, ...)
    roles (id, name)  -- ADMIN, CONTRIBUTOR, VIEWER
    user_roles (user_id, role_id)

V002__create_collections.sql
    collections (id, name, description, created_by, created_at)

V003__create_documents.sql
    documents (id, collection_id, title, category, product, revision,
               department, tags[], file_path, status, uploaded_by, ...)
    document_versions (id, document_id, revision, file_path, created_at)

V004__create_chunks_embeddings.sql   -- requires pgvector
    document_chunks (id BIGSERIAL, document_id, version_id, chunk_index,
                     content TEXT, page_number INT, section TEXT,
                     token_count INT)
    embeddings (id BIGSERIAL, chunk_id, model_name, embedding vector(1024))
    -- vector dim varies by model; default 1024 for bge-m3

V005__create_audit_licensing_branding.sql
    audit_log (id, user_id, action, entity_type, entity_id, details jsonb, created_at)
    license (id, license_key, tier, features jsonb, expires_at, is_active)
    branding_config (id, key, value)  -- company_name, logo_path, primary_color, ...
    system_settings (id, key, value)
```

### 3.2 Key Relationships

```
users ──< user_roles >── roles
collections ──< documents ──< document_versions
documents ──< document_chunks ──< embeddings
users ──< audit_log
```

---

## 4. API Surface (Contract First)

### 4.1 Spring Boot REST API (prefix `/api/v1`)

| Method | Path | Purpose | Roles |
|--------|------|---------|-------|
| POST | `/auth/login` | Username/password → JWT | public |
| POST | `/auth/refresh` | Refresh token | any |
| GET | `/users` | List users | ADMIN |
| POST/PUT/DELETE | `/users/{id}` | Manage users | ADMIN |
| GET/POST | `/collections` | List/create collections | any |
| POST | `/documents/upload` | Multipart upload → stores file, creates metadata, enqueues processing | ADMIN, CONTRIBUTOR |
| GET | `/documents` | List/filter documents | any |
| GET | `/documents/{id}/versions` | Version history | any |
| POST | `/documents/{id}/reindex` | Re-trigger indexing | ADMIN, CONTRIBUTOR |
| DELETE | `/documents/{id}` | Soft/archive delete | ADMIN |
| POST | `/search` | Hybrid search proxy → FastAPI | any |
| POST | `/chat` | RAG proxy → FastAPI (SSE stream) | any |
| GET | `/documents/{id}/source` | Download/open source doc, jump to page | any |
| GET/PUT | `/admin/branding` | Read/update branding config | ADMIN |
| GET/PUT | `/admin/license` | License info/activation | ADMIN |
| GET | `/admin/settings` | System settings | ADMIN |
| GET | `/admin/metrics` | Dashboard stats (doc count, chunks, storage, AI usage) | ADMIN |
| GET | `/admin/audit` | Audit log query | ADMIN |

> **Frontend never calls FastAPI directly.** All AI requests route through Spring Boot, which enforces authz, writes audit logs, then proxies to FastAPI. This preserves the "swap the AI engine" boundary.

### 4.2 FastAPI Internal API (prefix `/internal/v1` — not exposed to frontend)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/ingest` | Accept file path + metadata → extract → chunk → embed → store |
| GET | `/ingest/{job_id}` | Poll async ingestion job status |
| POST | `/search` | Hybrid (keyword + vector) search → ranked chunks + citations |
| POST | `/chat` | RAG: retrieve → context assembly → LLM (streamed) |
| GET | `/health` | Liveness + model connectivity check |

### 4.3 Inter-Service Contract Notes

- Spring Boot ↔ FastAPI authenticated via a shared internal service token (`X-Internal-Token` header), configured via env.
- File handoff: Spring Boot stores uploaded files to `storage/documents/originals/{uuid}.{ext}` and passes the path to FastAPI. FastAPI writes processed artifacts to `storage/documents/processed/`.
- All async work in FastAPI uses a background task queue (start with `asyncio` tasks + in-memory job table; upgrade to Celery/RQ in a later phase if needed).

---

## 5. Phased Delivery Plan

### Phase 0 — Foundation & Environments (Week 1)

- [ ] Create root `docker-compose.yml` orchestrating: `postgres` (pgvector), `ollama`, `backend`, `ai-service`, `frontend`, `nginx` (reverse proxy).
- [ ] Create root `.env.example` with all connection strings, secrets, ports.
- [ ] Add `database/` directory with `pgvector` init script and seed data.
- [ ] Configure Spring Boot `application.yaml`: datasource, Flyway, JWT secret, FastAPI base URL, storage paths.
- [ ] Verify `docker compose up` boots Postgres + runs Flyway migrations cleanly.
- [ ] Set up FastAPI project skeleton: `requirements.txt`, `app/main.py`, health endpoint, settings via `pydantic-settings`.

**Deliverable:** `docker compose up -d` starts all services; DB migrations run; health endpoints respond.

### Phase 1 — Backend Core: Auth, Users, Roles (Weeks 2–3)

- [ ] Spring Security config: JWT stateless auth, password hashing (BCrypt), role-based authorization (`ADMIN`/`CONTRIBUTOR`/`VIEWER`).
- [ ] Implement `auth`, `users`, `roles` packages per `directorystructure.md`.
- [ ] REST: `/auth/login`, `/users` CRUD.
- [ ] Seed default admin user + roles via Flyway or `ApplicationRunner`.
- [ ] Unit + slice tests (Spring Security test slice, MockMvc).
- [ ] Add audit logging interceptor (writes to `audit_log`).

**Deliverable:** Login returns JWT; protected endpoints enforce roles; admin can manage users.

### Phase 2 — Document Management & Collections (Weeks 3–4)

- [ ] `documents` + `collections` + `metadata` packages in Spring Boot.
- [ ] File upload endpoint: multipart → store to `storage/documents/originals/` → persist metadata + version record.
- [ ] Collections CRUD, document↔collection assignment.
- [ ] Version management: upload revision, archive, list history.
- [ ] Reindex endpoint (enqueues FastAPI job — stubbed until Phase 3).
- [ ] Frontend: Login page, Dashboard shell, Collections view, Documents list + Upload (drag & drop, bulk, folder), Document detail.

**Deliverable:** Users can log in, create collections, upload PDFs with metadata, see version history.

### Phase 3 — AI Service: Ingestion Pipeline (Weeks 5–6)

- [ ] FastAPI `ingestion/`: extractors (PDF/DOCX/TXT/HTML/CSV via `pypdf`/`python-docx`/`beautifulsoup4`/`pandas`).
- [ ] Chunkers: recursive text splitter with overlap; preserve page number + section metadata for citations.
- [ ] Embeddings service: Ollama `nomic-embed-text` / `bge-m3` / `mxbai-embed-large` (configurable).
- [ ] Vectorstore: store chunks + embeddings in pgvector (`document_chunks`, `embeddings`).
- [ ] Wire Spring Boot `/documents/upload` → FastAPI `/ingest` (async job). Poll `/ingest/{job_id}` for status; update document status.
- [ ] Frontend: show ingestion progress/status per document.

**Deliverable:** Upload a PDF → it is extracted, chunked, embedded, and searchable.

### Phase 4 — Search & RAG Chat (Weeks 7–8)

- [ ] FastAPI `rag/`: hybrid retriever (pgvector cosine + PostgreSQL full-text `tsvector`), reciprocal rank fusion ranker.
- [ ] RAG pipeline: context assembly with citations, prompt templates in `rag/prompts/`.
- [ ] LLM integration: Ollama chat models (Qwen/Llama/Gemma/DeepSeek), streamed via SSE.
- [ ] Spring Boot `/search` + `/chat` proxies with authz + audit logging.
- [ ] Frontend: AI Search page (semantic + keyword + hybrid toggle), Chat assistant with streamed answers + inline citations.
- [ ] Source Viewer: open source PDF, jump to cited page/section.

**Deliverable:** Core value proposition — "ask a question, get an answer with citations linked to source pages."

### Phase 5 — White Label, Licensing, Admin (Weeks 9–10)

- [ ] Branding: `branding_config` table + admin UI (company name, logo upload, primary/secondary color, login background, footer). Frontend reads branding at render time (SSR-injected or `/api/v1/branding`).
- [ ] Licensing: license key validation, tier (Basic/Professional/Enterprise), feature flags, expiration checks (interceptor/filter).
- [ ] Admin dashboard: metrics (doc count, collection count, indexed chunks, storage usage, AI usage), audit log viewer.
- [ ] System settings page.

**Deliverable:** Fully brandable, license-controlled, admin-managed platform.

### Phase 6 — Hardening, Deployment, Docs (Weeks 11–12)

- [ ] OCR support (Tesseract/EasyOCR) as optional pipeline step — feature-flagged.
- [ ] Performance: verify search < 2s, AI < 10s, upload ack < 5s (PRD non-functional requirements).
- [ ] pgvector index tuning (HNSW/IVFFlat) for 5M-chunk target.
- [ ] Backup/restore scripts (`deployment/scripts/backup.sh`, `restore.sh`).
- [ ] Nginx reverse proxy config, TLS termination.
- [ ] End-to-end Docker Compose hardening: healthchecks, restart policies, volumes.
- [ ] API specs (OpenAPI for Spring Boot + FastAPI), deployment guide, admin runbook.
- [ ] Integration + e2e test suite (`tests/`).

**Deliverable:** Production-ready, self-hosted MVP deployable via `docker compose up -d`.

---

## 6. Key Engineering Decisions & Risks

### 6.1 Decisions

1. **Spring Data JDBC over JPA.** The scaffold uses `spring-boot-starter-jdbc`. Flyway owns all DDL. We map rows to records/POJOs with `RowMapper` / `SimpleJdbcInsert`. This keeps the business layer lean and avoids Hibernate/L1-cache surprises at the scale of 50k docs. *(If the team strongly prefers JPA, swap `starter-jdbc` for `starter-data-jpa` and add entities — schema is unchanged.)*
2. **Single shared PostgreSQL instance.** Spring Boot and FastAPI connect to the same Postgres with separate roles. Simpler ops for self-hosted deployments; pgvector is available to both.
3. **All frontend→AI traffic proxied via Spring Boot.** Keeps authz centralized and the AI engine swappable. Adds one network hop; acceptable given < 2s / < 10s latency budgets.
4. **Async ingestion starts lightweight** (asyncio in-process). No Celery/Redis dependency for MVP. Migrate to a real queue only if throughput demands.

### 6.2 Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Next.js 16 API drift (new conventions, `unstable_instant`, etc.) | High | Medium | Always consult `node_modules/next/dist/docs/` before frontend work; never rely on memorized Next 13–15 patterns. |
| Spring Boot 4.1 breaking changes (Security config, new starters) | Medium | High | Validate starter names in `pom.xml` (already using `webmvc`/`security`/`flyway` 4.1 artifacts). Write slice tests early. |
| pgvector dimension mismatch between embedding models | Medium | Medium | Store `model_name` per embedding; set vector dim per deployment. Re-index when model changes. |
| Ollama latency on CPU-only self-hosted deployments | High | High | Document GPU recommendation; support smaller models (Gemma 2B) as fallback; cache frequent queries. |
| 5M chunks scalability | Medium | High | Plan HNSW indexing from Phase 6; benchmark at 100k/500k/1M milestones. |
| Inter-service auth token leakage | Low | Critical | Internal token via env only; FastAPI not exposed externally (only via Spring Boot proxy / internal Docker network). |

---

## 7. Repository Structure (Target)

Reconciling `directorystructure.md` with the actual monorepo layout already in place:

```
KnowledgeVault-AI/
├── docker-compose.yml            # NEW — root orchestration
├── .env.example                  # NEW
├── README.md                     # NEW
├── docs/
│   ├── knowledgeVault_prd.md     # existing (source of truth)
│   ├── implementation-plan.md    # NEW — this document
│   ├── architecture.md           # NEW (Phase 6)
│   ├── deployment-guide.md       # NEW (Phase 6)
│   └── api-specs/                # NEW (Phase 6)
├── frontend/
│   └── knowledgevault-ui/        # existing Next.js 16 app
│       ├── app/                  # App Router routes
│       ├── components/           # NEW
│       ├── lib/                  # NEW (api client, auth)
│       └── ...
├── backend/
│   └── document-service/         # existing Spring Boot 4.1
│       └── src/main/java/com/kva/document_service/
│           ├── auth/ users/ roles/ documents/ collections/
│           ├── metadata/ licensing/ branding/ audit/
│           ├── configuration/ common/
│           └── aiservice/        # NEW — FastAPI client/proxy
├── ai-service/                   # NEW — FastAPI
│   ├── app/
│   │   ├── api/ ingestion/ embeddings/ vectorstore/ rag/ llm/ core/
│   ├── requirements.txt
│   └── Dockerfile
├── database/
│   ├── migrations/               # Flyway scripts (managed by backend)
│   └── seed-data/
├── storage/                      # mounted volume (gitignored)
│   └── documents/{originals,processed,archived}
├── deployment/
│   ├── docker/ nginx/ scripts/
└── tests/
    ├── integration/ e2e/ performance/
```

> Note: The existing Spring Boot package is `com.kva.document_service` (underscore). Keep as-is to avoid churn. The `directorystructure.md` `com.knowledgevault` naming is aspirational; we stay with `com.kva`.

---

## 8. Definition of Done (MVP — PRD "MVP Scope")

The MVP is complete when all of the following are demonstrable:

- [x] Authentication (login) with JWT
- [x] Role-based access (Admin / Contributor / Viewer)
- [x] White-label branding configurable with no code changes
- [x] PDF (and DOCX/TXT/HTML/CSV) upload with metadata
- [x] Collections with single/multi/all search scoping
- [x] Document versioning (revisions, archive, history)
- [x] Text extraction + chunking pipeline
- [x] Embedding generation (Ollama models) + pgvector storage
- [x] Semantic + hybrid search returning ranked results
- [x] AI chat with streamed answers + mandatory source citations (doc, revision, page, section)
- [x] Source viewer jumping to cited page
- [x] Audit logging (logins, uploads, deletes, searches, AI questions)
- [x] License management (keys, tiers, flags, expiration)
- [x] Admin dashboard metrics
- [x] One-command Docker Compose deployment
- [x] Performance targets met: search < 2s, AI < 10s, upload ack < 5s
- [x] Backup/restore procedures documented

---

## 9. Immediate Next Steps

1. **Approve this plan.**
2. Begin **Phase 0**: create root `docker-compose.yml`, `.env.example`, FastAPI skeleton, and run end-to-end boot test.
3. Proceed phase-by-phase, with each phase's deliverable verified before moving on.