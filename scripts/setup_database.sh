#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# setup_database.sh -- install the Bank DBMS schema into MySQL (run as an admin).
#
#   scripts/setup_database.sh [--with-sample-data] [--reset] [database]
#
#   database            target schema (default: bank_dbms)
#   --with-sample-data  also load sql/05_sample_data.sql (demo customers, accounts,
#                       seven months of transactions, loans)
#   --reset             DROP and re-create the database first. Without this flag
#                       the script refuses to touch a database that has tables.
#
# Admin credentials: MYSQL_ADMIN_USER (default root) and MYSQL_PWD (optional),
# standard mysql-client environment variables. Create the application user with
# sql/00_create_database.sql first (see README).
# -----------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

DB=bank_dbms
SAMPLE=0
RESET=0
for arg in "$@"; do
  case "$arg" in
    --with-sample-data) SAMPLE=1 ;;
    --reset) RESET=1 ;;
    -*) echo "Unknown option $arg" >&2; exit 2 ;;
    *) DB="$arg" ;;
  esac
done
ADMIN="${MYSQL_ADMIN_USER:-root}"
MYSQL=(mysql -u "$ADMIN" --default-character-set=utf8mb4)

if [[ ! "$DB" =~ ^[A-Za-z0-9_]+$ ]]; then echo "Invalid database name: $DB" >&2; exit 2; fi

if [[ $RESET -eq 1 ]]; then
  echo "Dropping and re-creating database $DB ..."
  "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$DB\`"
fi
"${MYSQL[@]}" -e "CREATE DATABASE IF NOT EXISTS \`$DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"

TABLES=$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB'")
if [[ "$TABLES" != "0" ]]; then
  echo "Database $DB already has $TABLES tables. Re-run with --reset to rebuild it (this deletes its data)." >&2
  exit 1
fi

for script in sql/01_schema.sql sql/02_triggers.sql sql/03_views.sql sql/04_reference_data.sql; do
  echo "Running $script"
  "${MYSQL[@]}" "$DB" < "$script"
done
if [[ $SAMPLE -eq 1 ]]; then
  echo "Running sql/05_sample_data.sql"
  "${MYSQL[@]}" "$DB" < sql/05_sample_data.sql
fi

echo "Verifying ledger invariants:"
"${MYSQL[@]}" -t "$DB" < sql/06_verification_queries.sql
echo "Database $DB is ready."
