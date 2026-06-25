#!/bin/bash
set -e

echo "Preparing KnowledgeVault database for Flyway..."

# Application schema creation is owned exclusively by Flyway in the backend
# service. Keep this directory free of versioned schema migrations so Docker's
# initialization order cannot create tables before their dependencies exist.

echo "Database preparation complete; Flyway will apply the schema."
