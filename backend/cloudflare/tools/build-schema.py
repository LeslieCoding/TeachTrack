from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
source = root / "src/main/resources/db/migration"
target = root / "cloudflare/migrations/0001_init.sql"
versions = [
    "V002__identity_and_students.sql",
    "V003__learning_domain.sql",
    "V004__question_bank.sql",
    "V005__exam_analysis.sql",
    "V006__mistakes_and_mastery.sql",
    "V007__practice.sql",
    "V008__sync_and_audit.sql",
    "V010__subject_seed.sql",
    "V013__sync_operations.sql",
]


def convert(sql: str) -> str:
    sql = re.sub(r"\btext\[\]", "text", sql, flags=re.I)
    sql = re.sub(r"\btimestamptz\b", "text", sql, flags=re.I)
    sql = re.sub(r"\buuid\b", "text", sql, flags=re.I)
    sql = re.sub(r"\bjsonb\b", "text", sql, flags=re.I)
    sql = re.sub(r"\bbytea\b", "blob", sql, flags=re.I)
    sql = re.sub(r"\bboolean\b", "integer", sql, flags=re.I)
    sql = re.sub(r"\bbigserial\b", "integer", sql, flags=re.I)
    sql = re.sub(r"\b(smallint|bigint)\b", "integer", sql, flags=re.I)
    sql = re.sub(r"(?<=\s)date(?=,|\n|\s+not\s+null)", "text", sql, flags=re.I)
    sql = re.sub(r"default\s+now\(\)", "default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))", sql, flags=re.I)
    sql = re.sub(r"default\s+false\b", "default 0", sql, flags=re.I)
    sql = re.sub(r"default\s+true\b", "default 1", sql, flags=re.I)
    return sql


parts = []
for version in versions:
    parts.append(f"-- {version}\n" + convert((source / version).read_text(encoding="utf-8")))

# Fold the later PostgreSQL ALTER migrations into the initial D1 schema.
parts[1] = parts[1].replace("    date text not null,\n", "    date text,\n", 1)
parts[2] = parts[2].replace(
    "    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),\n    deleted_at text\n);",
    "    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),\n    updated_by text,\n    deleted_at text\n);",
    1,
)
parts[3] = parts[3].replace(
    "    source_bbox text,\n    unique (exam_paper_id, question_number)",
    "    source_bbox text,\n    stem text not null default '',\n    standard_answer text not null default '',\n    unique (exam_paper_id, question_number)",
    1,
)

portable_indexes = """-- Portable indexes from V009 (PostgreSQL GIN/trigram indexes are not used by D1)
create index if not exists questions_filter_idx on questions(subject_id, grade, question_type, difficulty)
    where deleted_at is null;
create index if not exists questions_review_idx on questions(review_status) where deleted_at is null;
create index if not exists questions_stem_hash_idx on questions(stem_hash);
create index if not exists questions_normalized_hash_idx on questions(normalized_hash);
create index if not exists question_knowledge_kp_idx on question_knowledge(knowledge_point_id);
create index if not exists student_answers_paper_idx on student_answers(exam_paper_id);
"""
parts.append(portable_indexes)

schema = "\n\n".join(parts)
triggers = []
for match in re.finditer(r"create table (\w+)\s*\((.*?)\);", schema, flags=re.S | re.I):
    table, body = match.groups()
    if re.search(r"\bupdated_at\b", body):
        triggers.append(
            f"create trigger trg_{table}_updated_at after update on {table}\n"
            f"for each row when new.updated_at = old.updated_at\n"
            f"begin\n"
            f"    update {table} set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;\n"
            f"end;"
        )

header = """-- TeachTrack D1/SQLite schema, generated from the final PostgreSQL migrations.
-- UUIDs, dates, timestamps and JSON/arrays are stored as TEXT; booleans as INTEGER.
-- API property names and values remain compatible with the Spring Boot backend.
-- Regenerate with: python tools/build-schema.py

"""
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(header + schema + "\n\n-- updated_at maintenance\n" + "\n\n".join(triggers) + "\n", encoding="utf-8")
print(f"wrote {target} ({len(triggers)} updated_at triggers)")
