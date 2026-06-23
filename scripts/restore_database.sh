#!/bin/bash
# Database restore script for KnowledgeVault

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "======================================"
echo "KnowledgeVault Database Restore"
echo "======================================"
echo ""

# Check if backup file is provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: No backup file specified${NC}"
    echo "Usage: $0 <backup_file.sql.gz>"
    echo ""
    echo "Available backups:"
    ls -lh /backups/*.sql.gz 2>/dev/null | awk '{print "  " $9, "(" $5 ")"}'
    exit 1
fi

BACKUP_FILE=$1

# Check if backup file exists
if [ ! -f "$BACKUP_FILE" ]; then
    echo -e "${RED}Error: Backup file not found: $BACKUP_FILE${NC}"
    exit 1
fi

# Configuration
DB_NAME="${DB_NAME:-knowledgevault}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

# Check if postgres is running
if ! docker ps | grep -q postgres; then
    echo -e "${RED}Error: PostgreSQL container is not running${NC}"
    exit 1
fi

# Display backup info
BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
BACKUP_DATE=$(stat -c %y "$BACKUP_FILE" 2>/dev/null || stat -f "%Sm" "$BACKUP_FILE")

echo "Backup file: $BACKUP_FILE"
echo "Size: $BACKUP_SIZE"
echo "Date: $BACKUP_DATE"
echo "Database: $DB_NAME"
echo ""

# Confirm restore
echo -e "${YELLOW}WARNING: This will overwrite the existing database!${NC}"
read -p "Are you sure you want to restore? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Restore cancelled"
    exit 0
fi

echo ""
echo "Starting restore process..."

# Kill existing connections
echo "Dropping existing connections..."
docker exec -i knowledgevault-db psql -U ${DB_USER} -d postgres -c "
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = '${DB_NAME}'
AND pid <> pg_backend_pid();
" > /dev/null 2>&1

# Drop and recreate database
echo "Recreating database..."
docker exec -i knowledgevault-db psql -U ${DB_USER} -d postgres -c "DROP DATABASE IF EXISTS ${DB_NAME};" > /dev/null 2>&1
docker exec -i knowledgevault-db psql -U ${DB_USER} -d postgres -c "CREATE DATABASE ${DB_NAME};" > /dev/null 2>&1

# Restore backup
echo "Restoring data..."
gunzip -c "$BACKUP_FILE" | docker exec -i knowledgevault-db psql -U ${DB_USER} -d ${DB_NAME}

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Database restored successfully${NC}"
    echo "  Database: $DB_NAME"
    echo "  From: $BACKUP_FILE"
else
    echo -e "${RED}✗ Restore failed${NC}"
    exit 1
fi

# Re-run migrations to ensure schema is up to date
echo ""
echo "Running database migrations..."
docker exec -i knowledgevault-db psql -U ${DB_USER} -d ${DB_NAME} -c "SELECT version;" > /dev/null 2>&1

echo ""
echo "======================================"
echo "Restore Complete"
echo "======================================"
echo "Database: $DB_NAME"
echo "Source: $BACKUP_FILE"
echo "Time: $(date)"
echo "======================================"