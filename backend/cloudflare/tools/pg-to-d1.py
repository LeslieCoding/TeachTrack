"""Export the existing TeachTrack PostgreSQL database and generate idempotent D1 SQL."""
from datetime import datetime, timezone
from pathlib import Path
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT.parent
PSQL = Path(os.environ.get("PG_BIN", "D:/SoftWare/pgsql-16/bin")) / "psql.exe"
HOST = os.environ.get("PGHOST", "127.0.0.1")
PORT = os.environ.get("PGPORT", "55432")
USER = os.environ.get("PGUSER", "teachtrack")
DATABASE = os.environ.get("PGDATABASE", "teachtrack")
OUT = ROOT / "data"
SCHEMA = (ROOT / "migrations/0001_init.sql").read_text(encoding="utf-8")


def psql(sql: str) -> str:
    result = subprocess.run(
        [str(PSQL), "-h", HOST, "-p", PORT, "-U", USER, "-d", DATABASE, "-Atq", "-c", sql],
        check=True, capture_output=True, text=True, encoding="utf-8",
    )
    return result.stdout.strip()


def table_dependencies() -> dict[str, set[str]]:
    dependencies: dict[str, set[str]] = {}
    for match in re.finditer(r"create table (\w+)\s*\((.*?)\);", SCHEMA, flags=re.S | re.I):
        table, body = match.groups()
        dependencies[table] = {
            ref for ref in re.findall(r"references\s+(\w+)\s*\(", body, flags=re.I) if ref != table
        }
    return dependencies


def ordered_tables() -> list[str]:
    dependencies = table_dependencies()
    ordered: list[str] = []
    remaining = set(dependencies)
    while remaining:
        ready = sorted(table for table in remaining if dependencies[table] <= set(ordered))
        if not ready:
            raise RuntimeError(f"foreign key cycle: {sorted(remaining)}")
        ordered.extend(ready)
        remaining.difference_update(ready)
    return ordered


def normalize_value(column: str, value):
    if value is None:
        return None
    if isinstance(value, bool):
        return 1 if value else 0
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if column == "token_hash" and isinstance(value, str):
        return value.removeprefix("\\x")
    if (column.endswith("_at") or column in {"generated_at", "occurred_at"}) and isinstance(value, str):
        try:
            parsed = datetime.fromisoformat(value.replace(" ", "T", 1))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
        except ValueError:
            return value
    return value


def sql_value(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def sort_tree_rows(table: str, rows: list[dict]) -> list[dict]:
    if table != "knowledge_points":
        return rows
    remaining = {row["id"]: row for row in rows}
    ordered: list[dict] = []
    inserted: set[str] = set()
    while remaining:
        ready = [row for row in remaining.values() if not row.get("parent_id") or row["parent_id"] in inserted]
        if not ready:
            raise RuntimeError("knowledge point parent ids do not form an importable tree")
        for row in ready:
            ordered.append(row)
            inserted.add(row["id"])
            del remaining[row["id"]]
    return ordered


def main() -> None:
    OUT.mkdir(exist_ok=True)
    exported: dict[str, list[dict]] = {}
    lines = ["-- Generated from PostgreSQL; INSERT OR IGNORE makes reruns idempotent."]
    for table in ordered_tables():
        raw = psql(f'select coalesce(json_agg(row_to_json(t)), \'[]\') from "{table}" t')
        rows = sort_tree_rows(table, json.loads(raw or "[]"))
        exported[table] = rows
        for row in rows:
            columns = list(row)
            values = [normalize_value(column, row[column]) for column in columns]
            lines.append(
                f'insert or ignore into "{table}" ({", ".join(chr(34) + c + chr(34) for c in columns)})\n'
                f'values ({", ".join(sql_value(value) for value in values)});'
            )
        print(f"{table}: {len(rows)}")
    (OUT / "pg-export.json").write_text(json.dumps(exported, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "d1-import.sql").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT / 'd1-import.sql'}")


if __name__ == "__main__":
    main()
