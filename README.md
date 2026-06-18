# KnowledgeVault-AI

An AI-powered document management system with semantic search capabilities using pgvector for vector similarity search.

## Overview

KnowledgeVault-AI is a full-stack application built with:
- **Backend:** Spring Boot 4.1.0 with Spring Security, JWT authentication, and JDBC
- **AI Service:** FastAPI with OpenAI integration for document processing
- **Frontend:** Next.js with React for user interface
- **Database:** PostgreSQL 16 with pgvector extension for vector embeddings

## Features

- 🔐 JWT-based authentication with role-based access control (RBAC)
- 👥 User management with admin, contributor, and viewer roles
- 📄 Document upload and management
- 📁 Collection organization
- 🔍 Semantic search using vector embeddings
- 📊 Audit logging for compliance
- 🐳 Docker Compose for easy deployment

## Quick Start

### Prerequisites
- Docker (20.10+)
- Docker Compose (2.0+)

### Using Docker (Recommended)

```bash
# Clone the repository
git clone https://github.com/jroneil/KnowledgeVault-AI.git
cd KnowledgeVault-AI

# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Test the API
curl http://localhost:8080/api/v1/health
```

For detailed Docker setup instructions, see [DOCKER_SETUP.md](DOCKER_SETUP.md).

### Default Credentials

- **Username:** `admin`
- **Password:** `admin123`

⚠️ **Change these credentials in production!**

## Project Structure

```
KnowledgeVault-AI/
├── backend/
│   └── document-service/          # Spring Boot backend
│       ├── src/
│       ├── pom.xml
│       └── Dockerfile
├── ai-service/                     # FastAPI AI service (Phase 3+)
├── frontend/
│   └── knowledgevault-ui/         # Next.js frontend (Phase 2+)
├── docs/                          # Documentation
├── database/
│   └── init/                      # Database initialization scripts
├── docker-compose.yml             # Docker Compose configuration
├── .env.example                   # Environment variables template
└── README.md
```

## Documentation

- [Implementation Plan](docs/implementation-plan.md) - Detailed implementation phases
- [Directory Structure](docs/directorystructure.md) - Project organization
- [Phase 1 Summary](backend/document-service/PHASE1_IMPLEMENTATION_SUMMARY.md) - Backend authentication & users
- [Docker Setup Guide](DOCKER_SETUP.md) - Complete Docker documentation

## API Endpoints

### Authentication
- `POST /api/v1/auth/login` - User login (returns JWT token)

### Users (Admin only)
- `GET /api/v1/users` - List all users
- `GET /api/v1/users/{id}` - Get user by ID
- `POST /api/v1/users` - Create new user
- `DELETE /api/v1/users/{id}` - Delete user

### Health
- `GET /api/v1/health` - Health check (public)
- `GET /api/v1/protected` - Test protected endpoint (authenticated)

## Development Status

### Phase 1: Backend Core ✅ COMPLETE
- [x] Spring Security with JWT authentication
- [x] User management (CRUD operations)
- [x] Role-based authorization (ADMIN/CONTRIBUTOR/VIEWER)
- [x] Audit logging
- [x] Database migrations with Flyway
- [x] Docker Compose setup

### Phase 2: Documents & Collections (Next)
- [ ] Document upload endpoint
- [ ] Document metadata storage
- [ ] Collections CRUD operations
- [ ] Document↔collection assignments
- [ ] Version management
- [ ] Frontend login page

### Phase 3: AI Service (Future)
- [ ] FastAPI service setup
- [ ] Document chunking
- [ ] OpenAI embedding generation
- [ ] Vector storage in pgvector

### Phase 4: Semantic Search (Future)
- [ ] Vector similarity search API
- [ ] Frontend search interface
- [ ] Search result ranking

### Phase 5: Advanced Features (Future)
- [ ] Document sharing
- [ ] Comments and annotations
- [ ] Advanced filtering
- [ ] Export functionality

### Phase 6: Production Readiness (Future)
- [ ] Comprehensive testing
- [ ] Performance optimization
- [ ] Security hardening
- [ ] API documentation (Swagger)
- [ ] Deployment guides

## Technology Stack

### Backend (Phase 1)
- **Framework:** Spring Boot 4.1.0
- **Language:** Java 17
- **Security:** Spring Security + JWT
- **Database:** PostgreSQL 16 + pgvector
- **Migrations:** Flyway
- **Build:** Maven
- **Authentication:** BCrypt + JWT (jjwt)

### AI Service (Phase 3)
- **Framework:** FastAPI
- **Language:** Python 3.11+
- **AI:** OpenAI API
- **Vector Database:** pgvector

### Frontend (Phase 2)
- **Framework:** Next.js 16.2
- **Language:** TypeScript
- **Styling:** Tailwind CSS
- **State:** React Context + hooks

## Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
# Database
POSTGRES_DB=knowledgevault
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password

# Backend
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/knowledgevault
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_secure_password
JWT_SECRET=your-secure-random-key-at-least-256-bits
JWT_EXPIRATION=86400000
```

## Testing the API

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Login as admin
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Access protected endpoint (requires JWT token)
curl http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <your-jwt-token>"
```

## Development

### Running Locally (Without Docker)

**Prerequisites:**
- Java 17+
- PostgreSQL 16
- Maven 3.9+

```bash
# Start PostgreSQL
docker run --name postgres -p 5432:5432 \
  -e POSTGRES_DB=knowledgevault \
  -e POSTGRES_PASSWORD=postgres \
  -d pgvector/pgvector:pg16

# Run backend
cd backend/document-service
mvnw.cmd clean install -DskipTests
mvnw.cmd spring-boot:run
```

### Making Changes

1. Edit code locally
2. Rebuild container: `docker compose up -d --build backend`
3. View logs: `docker compose logs -f backend`

## Contributing

This is a private project. Contribution guidelines will be established as the project matures.

## License

This is a private project. All rights reserved.

## Support

For issues and questions:
- Check the [documentation](docs/)
- Review [Docker Setup Guide](DOCKER_SETUP.md)
- Contact the project team directly

## Roadmap

See the [Implementation Plan](docs/implementation-plan.md) for detailed roadmap.

---

**Status:** Phase 1 Complete ✅ | Phase 2 In Progress 🚧 | Phase 3-6 Planned 📋

**Last Updated:** June 17, 2026