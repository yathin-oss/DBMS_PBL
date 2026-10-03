#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# export_sample_data.sh -- regenerate sql/05_sample_data.sql
#
# 1. scripts/setup_database.sh --reset bank_dbms        (empty schema + reference data)
# 2. java -cp target/bank-dbms.jar com.bankdbms.tools.SampleDataLoader
#        (drives the real services through March-September 2026)
# 3. scripts/export_sample_data.sh [database]           (this script)
#
# Dumps only rows that 04_reference_data.sql does not create, in foreign-key order,
# followed by the final balances of the five internal accounts.
# -----------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
DB="${1:-bank_dbms}"
ADMIN="${MYSQL_ADMIN_USER:-root}"
OUT=sql/05_sample_data.sql
DUMP=(mysqldump -u "$ADMIN" --no-create-info --skip-triggers --complete-insert --compact
      --skip-lock-tables --skip-add-locks --skip-tz-utc --set-gtid-purged=OFF --single-transaction --default-character-set=utf8mb4 "$DB")

dump() {  # table [where]
  echo ""
  echo "-- $1"
  if [[ $# -gt 1 ]]; then "${DUMP[@]}" "$1" --where="$2"; else "${DUMP[@]}" "$1"; fi
}

{
  cat <<'HEADER'
-- =============================================================================
-- 05_sample_data.sql  --  OPTIONAL demonstration data (load after 01-04)
--
-- Generated, not hand-written: com.bankdbms.tools.SampleDataLoader drove the real
-- Java services through seven months of activity (March-September 2026) and
-- scripts/export_sample_data.sh dumped the result. Every row therefore obeys the
-- application's business rules, and sql/06_verification_queries.sql reports zero
-- violations after loading.
--
-- Contents: 3 branches, 10 customers, 11 customer accounts (one joint, one FROZEN,
-- one BLOCKED, one CLOSED), 6 beneficiaries (one inactive), deposits, withdrawals,
-- 28 transfers, charges (one waived), a reversed deposit, and 3 loans:
--   VEHICLE  fully repaid (CLOSED)
--   PERSONAL one instalment overdue (due 2026-09-06), one repayment late
--   HOME     disbursed 2026-09-15, first instalment due 2026-10-15
-- Overdue status is derived from CURRENT_DATE, so more instalments show as
-- overdue as real time passes beyond October 2026.
-- =============================================================================
HEADER
  dump branch "branch_id > 1"
  dump customer
  dump account "account_id > 5"
  dump account_holder
  dump account_status_history "account_id > 5"
  dump bank_transaction "transaction_id > 1"
  dump transaction_entry "transaction_id > 1"
  dump beneficiary
  dump transfer
  dump charge
  dump loan
  dump loan_schedule
  dump loan_repayment
  echo ""
  echo "-- final balances of the internal accounts (they start at the 04_reference_data.sql values)"
  mysql -u "$ADMIN" -N "$DB" -e "SELECT CONCAT('UPDATE account SET balance = ', balance, ' WHERE account_id = ', account_id, ';') FROM account WHERE account_id <= 5 ORDER BY account_id"
} > "$OUT"
echo "Wrote $OUT ($(wc -l < "$OUT") lines)"
