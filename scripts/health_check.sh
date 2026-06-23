#!/bin/bash
# Health check script for KnowledgeVault services

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "======================================"
echo "KnowledgeVault Health Check"
echo "======================================"
echo "Time: $(date)"
echo ""

# Array of services to check
declare -A services=(
    ["postgres"]="5432"
    ["backend"]="8080"
    ["ai-service"]="8000"
    ["frontend"]="3000"
    ["redis"]="6379"
)

# Function to check service health
check_service() {
    local service=$1
    local port=$2
    local status=""
    local details=""
    
    # Check if container is running
    if docker ps | grep -q "knowledgevault-$service\|knowledgevault-db\|knowledgevault-ai\|knowledgevault-redis\|knowledgevault-frontend\|knowledgevault-backend"; then
        status="${GREEN}RUNNING${NC}"
    else
        status="${RED}STOPPED${NC}"
    fi
    
    # Check port availability
    if nc -z localhost $port 2>/dev/null; then
        details="Port $port: ${GREEN}OPEN${NC}"
    else
        details="Port $port: ${RED}CLOSED${NC}"
    fi
    
    # Service-specific checks
    case $service in
        "postgres")
            if docker exec knowledgevault-db pg_isready -U postgres &>/dev/null; then
                details="$details | ${GREEN}DB Ready${NC}"
            else
                details="$details | ${RED}DB Not Ready${NC}"
            fi
            ;;
        "ai-service")
            if curl -s http://localhost:8000/health &>/dev/null; then
                details="$details | ${GREEN}API Healthy${NC}"
            else
                details="$details | ${RED}API Unhealthy${NC}"
            fi
            ;;
        "backend")
            if curl -s http://localhost:8080/actuator/health &>/dev/null; then
                details="$details | ${GREEN}Backend Healthy${NC}"
            else
                details="$details | ${YELLOW}Backend Unreachable${NC}"
            fi
            ;;
        "redis")
            if docker exec knowledgevault-redis redis-cli ping &>/dev/null; then
                details="$details | ${GREEN}Redis OK${NC}"
            else
                details="$details | ${RED}Redis Down${NC}"
            fi
            ;;
    esac
    
    printf "%-15s %-15s %s\n" "$service" "$status" "$details"
}

# Function to check disk usage
check_disk() {
    echo ""
    echo "======================================"
    echo "Disk Usage"
    echo "======================================"
    
    docker system df
    
    echo ""
    echo "Volume Usage:"
    docker volume ls --format "table {{.Name}}\t{{.Driver}}" | while read line; do
        if [[ "$line" != "DRIVER" ]]; then
            volume=$(echo $line | awk '{print $1}')
            if [ ! -z "$volume" ]; then
                usage=$(docker run --rm -v $volume:/data alpine sh -c "du -sh /data" 2>/dev/null | awk '{print $1}')
                printf "%-30s %s\n" "$volume" "${usage:-N/A}"
            fi
        fi
    done
}

# Function to check memory usage
check_memory() {
    echo ""
    echo "======================================"
    echo "Container Resource Usage"
    echo "======================================"
    
    echo "Container             CPU %   MEM USAGE / LIMIT   MEM %   NET I/O"
    echo "--------------------- ------- -------------------- ------- ---------------"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}" | grep knowledgevault
}

# Function to check logs for errors
check_logs() {
    echo ""
    echo "======================================"
    echo "Recent Errors (Last 5 minutes)"
    echo "======================================"
    
    since="5m"
    
    echo ""
    echo "Backend errors:"
    docker logs --since $since knowledgevault-backend 2>&1 | grep -i "error\|exception" | tail -5 || echo "  No errors found"
    
    echo ""
    echo "AI Service errors:"
    docker logs --since $since knowledgevault-ai 2>&1 | grep -i "error\|exception" | tail -5 || echo "  No errors found"
    
    echo ""
    echo "Postgres errors:"
    docker logs --since $since knowledgevault-db 2>&1 | grep -i "error\|fatal" | tail -5 || echo "  No errors found"
}

# Function to check database statistics
check_db_stats() {
    echo ""
    echo "======================================"
    echo "Database Statistics"
    echo "======================================"
    
    if docker exec knowledgevault-db pg_isready -U postgres &>/dev/null; then
        docker exec knowledgevault-db psql -U postgres -d knowledgevault -c "
            SELECT 
                'Documents' as table_name, COUNT(*) as count 
            FROM documents
            UNION ALL
            SELECT 
                'Versions', COUNT(*) 
            FROM versions
            UNION ALL
            SELECT 
                'Files', COUNT(*) 
            FROM files
            UNION ALL
            SELECT 
                'Chunks', COUNT(*) 
            FROM document_chunks
            UNION ALL
            SELECT 
                'Embeddings', COUNT(*) 
            FROM embeddings;
        " 2>/dev/null | grep -v "table_name\|---\|rows"
    else
        echo "Database not accessible"
    fi
}

# Main execution
echo "Service Status"
echo "--------------------------------------"
printf "%-15s %-15s %s\n" "Service" "Status" "Details"
echo "--------------------------------------"

for service in "${!services[@]}"; do
    check_service "$service" "${services[$service]}"
done

check_db_stats
check_disk
check_memory

# Ask if user wants to check logs
echo ""
read -p "Check for recent errors? (y/n): " check_logs_input

if [ "$check_logs_input" = "y" ]; then
    check_logs
fi

echo ""
echo "======================================"
echo "Health Check Complete"
echo "======================================"
echo "Time: $(date)"
echo "======================================"