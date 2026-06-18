#!/bin/bash
set -e

echo "Initializing KnowledgeVault database..."

# This script is for any additional database initialization
# that needs to happen before Flyway migrations run
# Currently, Flyway handles all schema creation and migration

echo "Database initialization complete!"