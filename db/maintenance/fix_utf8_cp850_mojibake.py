#!/usr/bin/env python3
"""
Repairs a one-time data-corruption incident (2025-10-09): UTF-8 bytes that got
decoded as CP850 somewhere in the pipeline and were then stored as literal
mojibake text, e.g. "Prüfung" -> "Pr├╝fung".

The corruption is reversible: encoding the mojibake string back to CP850 bytes
recovers the original UTF-8 bytes exactly. See CodeOwl issue/incident writeup
for the investigation that found this (48 rows in
tiered_task_sequences.semantic_summary, plus a handful in task_description,
tasks.statement and tasks.solution, all created within one ~70-minute window).

This script is schema-generic — it scans every text/varchar column in the
database rather than a hardcoded list, so it is safe to point at a different
system (staging, production, another developer's DB) that may carry the same
incident's corruption or none at all.

Usage — direct psql (the DB's port is reachable from wherever you run this):
    export PGHOST=localhost PGPORT=5432 PGUSER=postgres PGPASSWORD=postgres PGDATABASE=codeowl
    # or: export DATABASE_URL=postgresql://user:pass@host:port/db

    python3 db/maintenance/fix_utf8_cp850_mojibake.py            # dry run: report only
    python3 db/maintenance/fix_utf8_cp850_mojibake.py --apply    # actually fix, in one transaction

Usage — DB only reachable via `docker exec` (no exposed port), run the script
from a host with the `docker` CLI pointed at that daemon, and pass the
container name instead of setting PG*:
    python3 db/maintenance/fix_utf8_cp850_mojibake.py --docker codeowl-clean-db
    python3 db/maintenance/fix_utf8_cp850_mojibake.py --docker codeowl-clean-db --apply

Otherwise requires only the `psql` client on PATH — no Python DB driver
needed, so it runs anywhere the CodeOwl backend's own `psql`/`pg_dump`
tooling already runs. With --docker, only the `docker` CLI is needed locally;
psql runs inside the container image.
"""
import argparse
import json
import subprocess
import sys

MARKER = "├"  # UTF-8 0xE2 0x94 0x9C — appears whenever a stored string still holds a mis-decoded byte.
DOLLAR_TAG = "$mojibakefix$"

# Set by main() from --docker; None means talk to psql directly via PG* env vars.
DOCKER_CONTAINER: str | None = None


def _psql_argv(extra: list[str]) -> list[str]:
    if DOCKER_CONTAINER:
        return ["docker", "exec", "-i", DOCKER_CONTAINER, "psql", "-U", "postgres", "-d", "codeowl", *extra]
    return ["psql", *extra]


def run_psql(sql: str) -> str:
    result = subprocess.run(
        _psql_argv(["-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", sql]),
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"psql failed:\n{result.stderr}")
    return result.stdout


def apply_psql(sql: str) -> None:
    result = subprocess.run(
        _psql_argv(["-v", "ON_ERROR_STOP=1"]),
        input=sql, capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"psql failed:\n{result.stderr}\n{result.stdout}")
    print(result.stdout)


def discover_text_columns() -> list[tuple[str, str]]:
    """Every text/varchar column in the public schema, base tables only (skips views)."""
    sql = """
    SELECT json_agg(json_build_array(c.table_name, c.column_name))
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND c.data_type IN ('text', 'character varying')
      AND t.table_type = 'BASE TABLE';
    """
    out = run_psql(sql).strip()
    return [tuple(pair) for pair in json.loads(out)] if out else []


def fix_mojibake(s: str) -> str:
    return s.encode("cp850").decode("utf-8")


def main():
    global DOCKER_CONTAINER

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apply", action="store_true", help="Actually run the UPDATEs (default: dry run / report only).")
    ap.add_argument("--samples", type=int, default=10, help="How many before/after samples to print.")
    ap.add_argument("--docker", metavar="CONTAINER",
                     help="Run psql inside this Docker container (docker exec) instead of connecting directly via PG* env vars.")
    args = ap.parse_args()
    DOCKER_CONTAINER = args.docker

    columns = discover_text_columns()
    print(f"Scanning {len(columns)} text/varchar columns for the mojibake marker ({MARKER!r})...\n")

    updates = []
    failures = []
    samples = []

    for table, col in columns:
        sql = f"""
        SELECT COALESCE(json_agg(row_to_json(t)), '[]') FROM (
            SELECT id, {col} AS val FROM {table} WHERE {col} LIKE '%{MARKER}%'
        ) t;
        """
        try:
            out = run_psql(sql).strip()
        except SystemExit:
            # Table has no `id` column, or some other shape mismatch — skip it rather than abort the scan.
            continue
        rows = json.loads(out) if out else []
        for row in rows:
            rid, original = row["id"], row["val"]
            try:
                fixed = fix_mojibake(original)
            except (UnicodeEncodeError, UnicodeDecodeError) as e:
                failures.append((table, col, rid, original, str(e)))
                continue
            if MARKER in fixed or "�" in fixed:
                failures.append((table, col, rid, original, "still contains marker/replacement char after fix"))
                continue
            updates.append((table, col, rid, fixed))
            if len(samples) < args.samples:
                samples.append((table, col, rid, original, fixed))

    print(f"Rows to fix: {len(updates)}")
    print(f"Rows needing manual review: {len(failures)}\n")

    if samples:
        print("=== Samples ===")
        for table, col, rid, orig, fixed in samples:
            print(f"[{table}.{col}] {rid}")
            print(f"  before: {orig}")
            print(f"  after:  {fixed}\n")

    if failures:
        print("=== NEEDS MANUAL REVIEW (not auto-fixed) ===")
        for table, col, rid, orig, err in failures:
            print(f"[{table}.{col}] {rid}: {err}")
            print(f"  value: {orig}")
        print()

    if not updates:
        print("Nothing to fix.")
        return

    if not args.apply:
        print(f"Dry run only — no changes made. Re-run with --apply to fix these {len(updates)} rows.")
        return

    sql_lines = ["BEGIN;"]
    for table, col, rid, fixed in updates:
        if DOLLAR_TAG in fixed:
            sys.exit(f"Refusing to apply: fixed value for {table}.{col} {rid} contains the SQL dollar-quote tag itself.")
        rid_escaped = rid.replace("'", "''")
        sql_lines.append(f"UPDATE {table} SET {col} = {DOLLAR_TAG}{fixed}{DOLLAR_TAG} WHERE id = '{rid_escaped}';")
    sql_lines.append("COMMIT;")

    print(f"Applying {len(updates)} updates in one transaction...")
    apply_psql("\n".join(sql_lines))
    print("Done.")


if __name__ == "__main__":
    main()
