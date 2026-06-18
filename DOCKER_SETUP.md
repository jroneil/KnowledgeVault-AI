# Docker Setup Guide

This guide explains how to set up and run KnowledgeVault-AI using Docker and Docker Compose.

---

## Prerequisites

Before you begin, ensure you have the following installed on your system:

- **Docker** (version 20.10 or higher)
  - Download from: https://www.docker.com/get-started
- **Docker Compose** (version 2.0 or higher)
  - Usually included with Docker Desktop
  - Verify installation: `docker compose version`

### Verify Installation
```bash
docker --version
docker compose version
```

---

## Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/jroneil/KnowledgeVault-AI.git
cd KnowledgeVault-AI
```

### 2. Configure Environment Variables (Optional)
```bash
# Copy the example environment file
cp .env.example .env

# Edit .env to customize your configuration
# You can use the default values for development
```

### 3. Start All Services
```bash
# Start all services in detached mode
docker compose up -d

# View logs
docker compose logs -f

# Or view logs for a specific service
docker compose logs -f backend
docker compose logs -f postgres
```

### 4. Verify Services are Running
```bash
# Check all services status
docker compose ps

# Expected output should show:
# - postgres: running (healthy)
# - backend: running (healthy)
```

### 5. Test the API
```bash
# Health check
curl http://localhost:8080/api/v1/health

# Login as admin (default credentials)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

## Services Overview

### PostgreSQL Database
- **Image:** `pgvector/pgvector:pg16`
- **Container:** `knowledgevault-db`
- **Port:** `5432:5432`
- **Database:** `knowledgevault`
- **Username:** `postgres`
- **Password:** `postgres`
- **Features:**
  - Includes pgvector extension for vector similarity search
  - Automatic health checks
  - Persistent data storage in Docker volume
  - Automatic database initialization scripts

### Backend Service (Spring Boot)
- **Container:** `knowledgevault-backend`
- **Port:** `8080:8080`
- **Base URL:** `http://localhost:8080`
- **Features:**
  - REST API with JWT authentication
  - Automatic Flyway database migrations
  - Health checks at `/api/v1/health`
  - Auto-restart on failure
  - Non-root user for security
  - Multi-stage Docker build for smaller image size

### Future Services (Coming in Later Phases)
- **AI Service (FastAPI)** - Will run on port 8000
- **Frontend (Next.js)** - Will run on port 3000

---

## Docker Compose Commands

### Start Services
```bash
# Start all services
docker compose up -d

# Start specific service
docker compose up -d postgres
docker compose up -d backend

# Start and view logs
docker compose up
```

### Stop Services
```bash
# Stop all services (keeps containers and volumes)
docker compose down

# Stop and remove all containers, networks, and volumes
docker compose down -v

# Stop specific service
docker compose stop backend
```

### View Logs
```bash
# View logs for all services
docker compose logs

# Follow logs in real-time
docker compose logs -f

# View logs for specific service
docker compose logs -f backend

# View last 100 lines
docker compose logs --tail=100 backend
```

### Restart Services
```bash
# Restart all services
docker compose restart

# Restart specific service
docker compose restart backend
```

### Rebuild Services
```bash
# Rebuild and start
docker compose up -d --build

# Rebuild specific service
docker compose up -d --build backend

# Rebuild without cache
docker compose build --no-cache backend
```

### Execute Commands in Containers
```bash
# Access PostgreSQL container
docker compose exec postgres psql -U postgres -d knowledgevault

# Access backend container
docker compose exec backend sh

# Run command in backend
docker compose exec backend java -version
```

---

## Volumes

### postgres_data
- **Purpose:** Persistent PostgreSQL database storage
- **Location:** Docker managed volume
- **Contents:** All database tables, indexes, and data
- **Persistence:** Survives container restarts and recreation

### storage_data
- **Purpose:** File storage for uploaded documents
- **Location:** Docker managed volume
- **Contents:** User-uploaded files and documents
- **Persistence:** Survives container restarts and recreation

### View Volumes
```bash
# List all volumes
docker volume ls

# Inspect a volume
docker volume inspect knowledgevault_postgres_data

# Remove a volume (CAUTION: deletes all data)
docker volume rm knowledgevault_postgres_data
```

---

## Networks

### knowledgevault-network
- **Type:** Bridge network
- **Purpose:** Isolated network for service communication
- **Benefits:**
  - Services can communicate using container names
  - Isolated from host network (except for exposed ports)
  - Service discovery enabled

---

## Environment Variables

### Using .env File
Create a `.env` file in the project root (copy from `.env.example`):

```bash
cp .env.example .env
```

Edit `.env` to customize:

```bash
# Database
POSTGRES_DB=knowledgevault
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password

# Backend
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/knowledgevault
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_secure_password
SERVER_PORT=8080

# JWT (CHANGE IN PRODUCTION!)
JWT_SECRET=your-very-secure-random-secret-key-at-least-256-bits
JWT_EXPIRATION=86400000
```

### Inline Environment Variables
You can also pass environment variables directly:

```bash
POSTGRES_PASSWORD=mypassword docker compose up -d
```

---

## Troubleshooting

### Service Won't Start

**Check logs:**
```bash
docker compose logs backend
```

**Common issues:**
1. **Port already in use**
   - Check what's using port 8080: `netstat -ano | findstr :8080`
   - Change port in `docker-compose.yml` or stop the conflicting service

2. **Database connection refused**
   - Wait for PostgreSQL to be healthy: `docker compose ps`
   - Check PostgreSQL logs: `docker compose logs postgres`

3. **Out of disk space**
   - Clean up unused Docker resources: `docker system prune -a`

### Database Issues

**Access database directly:**
```bash
docker compose exec postgres psql -U postgres -d knowledgevault
```

**Reset database (deletes all data):**
```bash
docker compose down -v
docker compose up -d
```

**View migrations:**
```bash
docker compose exec postgres psql -U postgres -d knowledgevault -c "SELECT * FROM flyway_schema_history;"
```

### Backend Issues

**Check backend health:**
```bash
curl http://localhost:8080/api/v1/health
```

**Check container health:**
```bash
docker inspect knowledgevault-backend | grep -A 10 Health
```

**Rebuild backend:**
```bash
docker compose up -d --build backend
```

### Permission Issues

If you encounter permission errors with volumes:
```bash
# On Linux, ensure proper permissions
sudo chown -R $USER:$USER .

# Or run Docker with proper user permissions
# See Docker documentation for your platform
```

---

## Production Considerations

⚠️ **IMPORTANT SECURITY NOTES FOR PRODUCTION:**

1. **Change Default Passwords**
   - Change PostgreSQL password in `.env`
   - Change JWT secret to a cryptographically secure random key
   - Change default admin password (admin123) immediately

2. **Generate Secure JWT Secret**
   ```bash
   # Generate a 256-bit key
   openssl rand -base64 32
   ```

3. **Use Environment-Specific Configuration**
   - Create separate `.env.production` file
   - Use secrets management tool (Docker Secrets, HashiCorp Vault, etc.)

4. **Enable SSL/TLS**
   - Configure PostgreSQL for SSL connections
   - Use HTTPS for backend API
   - Configure proper certificates

5. **Resource Limits**
   - Set memory and CPU limits in `docker-compose.yml`
   - Monitor resource usage

6. **Backup Strategy**
   - Regular database backups
   - Volume snapshots
   - Off-site backup storage

7. **Network Security**
   - Use private networks where possible
   - Configure firewall rules
   - Restrict access to sensitive ports

8. **Logging and Monitoring**
   - Centralized logging (ELK stack, etc.)
   - Metrics collection (Prometheus, etc.)
   - Alerting system

---

## Development Workflow

### Making Code Changes

1. **Edit code locally**
2. **Rebuild backend container:**
   ```bash
   docker compose up -d --build backend
   ```
3. **Test changes**
4. **View logs:**
   ```bash
   docker compose logs -f backend
   ```

### Hot Reload (Optional)

For faster development, consider using volume mounts (not recommended for production):

Add to `docker-compose.yml` backend service:
```yaml
volumes:
  - ./backend/document-service/src:/app/src:ro
```

### Database Migrations

1. Create new migration file in `backend/document-service/src/main/resources/db/migration/`
2. Rebuild backend: `docker compose up -d --build backend`
3. Flyway will automatically run new migrations on startup

---

## Cleaning Up

### Remove All Containers and Networks
```bash
docker compose down
```

### Remove All Containers, Networks, and Volumes
```bash
docker compose down -v
```

### Remove Docker System Resources
```bash
# Remove unused images, containers, networks, and volumes
docker system prune -a

# Remove only stopped containers and unused networks
docker system prune
```

---

## Performance Tips

### Build Optimization
- Use Docker build cache
- Layer Dockerfile properly (least frequently changing files first)
- Use `.dockerignore` to reduce build context

### Runtime Optimization
- Set appropriate resource limits
- Use health checks for auto-restart
- Enable container logging rotation

### Database Optimization
- Tune PostgreSQL settings in `postgresql.conf`
- Add appropriate indexes
- Regular vacuum and analyze operations

---

## Additional Resources

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)
- [Spring Boot Docker Guide](https://spring.io/guides/topicals/spring-boot-docker/)

---

## Support

For issues or questions:
1. Check this documentation
2. Check logs: `docker compose logs`
3. Review Phase 1 Implementation Summary
4. Open an issue on GitHub

---

**Last Updated:** June 17, 2026  
**Docker Compose Version:** 3.8  
**Docker Engine Version:** 20.10+