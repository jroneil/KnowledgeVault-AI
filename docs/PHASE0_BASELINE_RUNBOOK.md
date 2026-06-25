# Phase 0 Baseline Runbook

## Configuration

Copy `.env.example` to `.env` and replace every `replace-with-*` value before
using the deployment outside local development.

The public frontend uses same-origin `/api` requests. Next.js proxies those
requests to `BACKEND_INTERNAL_URL`, which defaults to `http://backend:8080`
inside Docker.

The backend and AI service share the following configuration contract:

| Variable | Purpose |
|---|---|
| `AI_SERVICE_BASE_URL` | Backend connection to FastAPI |
| `INTERNAL_API_KEY` | Backend-to-AI bearer token |
| `OLLAMA_BASE_URL` | FastAPI connection to Ollama |
| `OLLAMA_EMBEDDING_MODEL` | Embedding model |
| `OLLAMA_LLM_MODEL` | Chat model |
| `CORS_ALLOWED_ORIGINS` | Browser origins accepted by application services |

## Local validation

From PowerShell at the repository root:

```powershell
.\scripts\validate.ps1
```

Individual checks:

```powershell
docker compose config --quiet
python -m compileall -q ai-service/app
npm.cmd --prefix frontend/knowledgevault-ui run lint
npm.cmd --prefix frontend/knowledgevault-ui run build
backend/document-service/mvnw.cmd test
```

## Clean startup

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Expected health endpoints:

- Frontend: `http://localhost:3000/login`
- Backend: `http://localhost:8080/api/v1/health`
- AI service: `http://localhost:8000/health`
- AI dependencies: `http://localhost:8000/api/v1/health/detailed`

## Migration ownership

Flyway is the only owner of the application schema. Docker initialization
scripts must not contain versioned application migrations. The chunks and
embeddings schema is migration `V005` in the Spring Boot service and therefore
runs only after users, collections, documents, and versions exist.
