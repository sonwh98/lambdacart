#!/usr/bin/env bash
set -euo pipefail

URI="datomic:sql://lambdacart?jdbc:postgresql://localhost:5432/datomic?user=$DATOMIC_USER&password=$DATOMIC_PASSWORD"
BACKUP_DIR=$(pwd)/lambdacart-$(date +'%Y-%m-%d_%H-%M-%S')

echo "🧱 Dumping $URI to file:$BACKUP_DIR ..."
$DATOMIC_HOME/bin/datomic backup-db "$URI" file:$BACKUP_DIR

# Compress the backup directory
tar -czf ${BACKUP_DIR}.tar.gz -C $(dirname $BACKUP_DIR) $(basename $BACKUP_DIR)

# Delete the original uncompressed directory
rm -rf $BACKUP_DIR

echo "✅ Backup complete: ${BACKUP_DIR}.tar.gz"
