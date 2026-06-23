#!/bin/bash
# Automated PostgreSQL database backup script for KnowledgeVault

# Configuration
DB_NAME="${DB_NAME:-knowledgevault}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
DB_HOST="${DB_HOST:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/knowledgevault_backup_${TIMESTAMP}.sql.gz"
RETENTION_DAYS=7

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "======================================"
echo "KnowledgeVault Database Backup"
echo "======================================"
echo "Time: $(date)"
echo ""

# Create backup directory if it doesn't exist
mkdir -p ${BACKUP_DIR}

# Check if postgres is running
if ! docker ps | grep -q postgres; then
    echo -e "${RED}Error: PostgreSQL container is not running${NC}"
    exit 1
fi

echo "Starting backup process..."
echo "Database: ${DB_NAME}"
echo "Host: ${DB_HOST}"
echo "Backup file: ${BACKUP_FILE}"

# Perform backup
docker exec -i knowledgevault-db pg_dump -U ${DB_USER} ${DB_NAME} | gzip > ${BACKUP_FILE}

if [ $? -eq 0 ]; then
    BACKUP_SIZE=$(du -h ${BACKUP_FILE} | cut -f1)
    echo -e "${GREEN}✓ Backup completed successfully${NC}"
    echo "  File size: ${BACKUP_SIZE}"
    echo "  Location: ${BACKUP_FILE}"
else
    echo -e "${RED}✗ Backup failed${NC}"
    exit 1
fi

# Clean up old backups (keep only last RETENTION_DAYS days)
echo ""
echo "Cleaning up old backups (keeping last ${RETENTION_DAYS} days)..."
find ${BACKUP_DIR} -name "knowledgevault_backup_*.sql.gz" -mtime +${RETENTION_DAYS} -delete

DELETED_COUNT=$(find ${BACKUP_DIR} -name "knowledgevault_backup_*.sql.gz" | wc -l)
echo "  Current backups: ${DELETED_COUNT}"

# Display backup summary
echo ""
echo "======================================"
echo "Backup Summary"
echo "======================================"
echo "Timestamp: ${TIMESTAMP}"
echo "File: ${BACKUP_FILE}"
echo "Size: ${BACKUP_SIZE}"
echo "Retention: ${RETENTION_DAYS} days"
echo "======================================"