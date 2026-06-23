# Docker Deployment Guide

## Prerequisites
- Docker and Docker Compose installed
- Git (for cloning the repository)

## Quick Start

### 1. Clone and Navigate
```bash
cd e:/KnowledgeVault-AI
```

### 2. Start All Services
```bash
docker-compose up -d
```

This will start:
- **PostgreSQL Database** (port 5432)
- **Backend API** (port 8080)
- **Frontend UI** (port 3000)
- **pgAdmin** (port 8081)

### 3. View Application
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- pgAdmin: http://localhost:8081

## Service Details

### Frontend (knowledgevault-frontend)
- **Port:** 3000
- **Image:** Built from `frontend/knowledgevault-ui/Dockerfile`
- **Environment Variables:**
  - `NEXT_PUBLIC_API_URL=http://backend:8080`
  - `NODE_ENV=production`
- **Dependencies:** Backend service
- **Restart Policy:** unless-stopped

### Backend (knowledgevault-backend)
- **Port:** 8080
- **Image:** Built from `backend/document-service/Dockerfile`
- **Environment Variables:**
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/knowledgevault`
  - `SPRING_DATASOURCE_USERNAME=postgres`
  - `SPRING_DATASOURCE_PASSWORD=postgres`
  - `JWT_SECRET=KnowledgeVault-AI-Secret-Key-Change-In-Production-Min-256-Bits`
  - `JWT_EXPIRATION=86400000`
- **Volumes:** `storage_data` for document storage
- **Dependencies:** PostgreSQL (with health check)
- **Restart Policy:** unless-stopped

### PostgreSQL (knowledgevault-db)
- **Port:** 5432
- **Image:** pgvector/pgvector:pg16
- **Environment Variables:**
  - `POSTGRES_DB=knowledgevault`
  - `POSTGRES_USER=postgres`
  - `POSTGRES_PASSWORD=postgres`
- **Volumes:** `postgres_data` for database persistence
- **Health Check:** Ready when PostgreSQL accepts connections
- **Restart Policy:** unless-stopped

### pgAdmin (knowledgevault-pgadmin)
- **Port:** 8081
- **Image:** dpage/pgadmin4:latest
- **Environment Variables:**
  - `PGADMIN_DEFAULT_EMAIL=admin@knowledgevault.com`
  - `PGADMIN_DEFAULT_PASSWORD=admin123`
- **Dependencies:** PostgreSQL (with health check)
- **Restart Policy:** unless-stopped

## Docker Compose Commands

### Start Services
```bash
docker-compose up -d
```

### Stop Services
```bash
docker-compose down
```

### Stop and Remove Volumes
```bash
docker-compose down -v
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f frontend
docker-compose logs -f backend
docker-compose logs -f postgres
```

### Rebuild Services
```bash
# Rebuild all
docker-compose up -d --build

# Rebuild specific service
docker-compose up -d --build frontend
docker-compose up -d --build backend
```

### Check Service Status
```bash
docker-compose ps
```

### Execute Commands in Containers
```bash
# Backend
docker-compose exec backend bash

# Frontend
docker-compose exec frontend sh

# PostgreSQL
docker-compose exec postgres psql -U postgres -d knowledgevault
```

## Volumes

### postgres_data
- Stores PostgreSQL database files
- Location: Docker managed volume
- Persistence: Yes

### storage_data
- Stores uploaded document files
- Location: Docker managed volume
- Mapped to: `/app/storage` in backend container
- Structure:
  ```
  /app/storage/
  ├── documents/
  │   ├── originals/
  │   │   └── {collection_id}/
  │   │       └── {document_id}/
  │   │           ├── v1_original.pdf
  │   │           ├── v2_updated.pdf
  │   │           └── v3_final.pdf
  ```

## Networking

All services communicate via `knowledgevault-network` bridge network:
- Frontend → Backend (http://backend:8080)
- Backend → PostgreSQL (postgresql://postgres:5432/knowledgevault)
- pgAdmin → PostgreSQL

## Troubleshooting

### Frontend Can't Connect to Backend
1. Check if backend is running:
   ```bash
   docker-compose ps backend
   ```

2. Check backend logs:
   ```bash
   docker-compose logs backend
   ```

3. Verify frontend environment variable:
   ```bash
   docker-compose exec frontend env | grep NEXT_PUBLIC_API_URL
   ```

### Database Connection Issues
1. Check PostgreSQL health:
   ```bash
   docker-compose exec postgres pg_isready -U postgres
   ```

2. Check database logs:
   ```bash
   docker-compose logs postgres
   ```

3. Test connection from backend:
   ```bash
   docker-compose exec backend curl postgres:5432
   ```

### File Upload Issues
1. Check storage volume:
   ```bash
   docker volume ls
   docker volume inspect knowledgevault-ai_storage_data
   ```

2. Verify storage directory:
   ```bash
   docker-compose exec backend ls -la /app/storage/
   ```

### Port Conflicts
If ports are already in use, modify `docker-compose.yml`:
```yaml
ports:
  - "3001:3000"  # Frontend on port 3001
  - "8081:8080"  # Backend on port 8081
```

### Rebuild After Code Changes
```bash
# Stop services
docker-compose down

# Rebuild and start
docker-compose up -d --build

# View logs during startup
docker-compose logs -f
```

## Production Deployment

### Security Considerations
1. **Change Default Credentials:**
   - Update JWT_SECRET in docker-compose.yml
   - Change PostgreSQL password
   - Change pgAdmin credentials

2. **Use Environment File:**
   ```bash
   # Create .env file
   cp .env.example .env
   # Edit .env with production values
   docker-compose --env-file .env up -d
   ```

3. **Enable HTTPS:**
   - Use reverse proxy (nginx/traefik)
   - Configure SSL certificates
   - Update frontend to use HTTPS

4. **Resource Limits:**
   Add to docker-compose.yml:
   ```yaml
   deploy:
     resources:
       limits:
         cpus: '2'
         memory: 2G
       reservations:
         cpus: '1'
         memory: 1G
   ```

### Backup Strategy
```bash
# Backup PostgreSQL
docker-compose exec postgres pg_dump -U postgres knowledgevault > backup.sql

# Backup storage volume
docker run --rm -v knowledgevault-ai_storage_data:/data -v $(pwd):/backup alpine tar czf /backup/storage-backup.tar.gz -C /data .

# Restore PostgreSQL
cat backup.sql | docker-compose exec -T postgres psql -U postgres knowledgevault
```

## Monitoring

### Health Checks
All services have built-in health checks:
```bash
# Check all health statuses
docker-compose ps
```

### Resource Usage
```bash
# Docker stats
docker stats

# Specific container
docker stats knowledgevault-backend knowledgevault-frontend
```

## Next Steps

After successful deployment:
1. Access frontend at http://localhost:3000
2. Create a user account (if authentication is implemented)
3. Create a collection
4. Upload documents
5. Test version management
6. Try advanced search

## Support

For issues or questions:
- Check logs: `docker-compose logs -f`
- Verify service status: `docker-compose ps`
- Review documentation in `docs/` directory