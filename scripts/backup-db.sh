#!/bin/bash
# Backup script for Rede Social databases
# Requirement: 18.3 - Backup and disaster recovery
# Run: ./scripts/backup-db.sh [backup-dir]

BACKUP_DIR=${1:-./backups}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p "$BACKUP_DIR"

echo "Starting backup at $TIMESTAMP"

# PostgreSQL (User Service)
if command -v pg_dump &> /dev/null; then
  pg_dump -h localhost -U postgres rede_social > "$BACKUP_DIR/postgres_$TIMESTAMP.sql"
  echo "PostgreSQL backup completed"
fi

# MongoDB (Content, Messaging, Notifications)
if command -v mongodump &> /dev/null; then
  mongodump --uri="mongodb://admin:admin@localhost:27017/rede_social?authSource=admin" --out="$BACKUP_DIR/mongodb_$TIMESTAMP"
  echo "MongoDB backup completed"
fi

echo "Backup finished at $(date)"
