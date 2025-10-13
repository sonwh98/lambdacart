#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <backup-file.tar.gz>"
  exit 1
fi

BACKUP_TAR=$1
BACKUP_DIR=${BACKUP_TAR%.tar.gz}

# Unpack the backup
tar -xzf $BACKUP_TAR

# Build Datomic URIs
SRC_URI=file:$(pwd)/$BACKUP_DIR
DST_URI=$(printf 'datomic:sql://lambdacart?jdbc:postgresql://localhost:5432/datomic?user=%s&password=%s' \
              "$DATOMIC_USER" "$DATOMIC_PASSWORD")

# Restore database
$DATOMIC_HOME/bin/datomic restore-db $SRC_URI $DST_URI

echo "✅ Restore complete: $DST_URI"
