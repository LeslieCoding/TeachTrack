-- V008: sync, audit, imports

create table sync_devices (
    id uuid primary key,
    user_id uuid not null references users(id),
    device_name text not null,
    platform text not null default 'android',
    app_version text not null default '',
    last_seen_at timestamptz,
    last_cursor bigint not null default 0,
    created_at timestamptz not null default now()
);

create table sync_changes (
    seq bigserial primary key,
    entity_type text not null,
    entity_id uuid not null,
    operation text not null check (operation in ('UPSERT','DELETE')),
    version integer not null,
    device_id uuid,
    payload jsonb,
    changed_at timestamptz not null default now()
);
create index sync_changes_entity_idx on sync_changes(entity_type, entity_id, seq desc);
create index sync_changes_seq_idx on sync_changes(seq);

create table audit_logs (
    id uuid primary key,
    actor_type text not null check (actor_type in ('USER','AI','SYSTEM')),
    actor_id text,
    token_id uuid,
    provider text,
    model text,
    request_id text,
    device_id text,
    action text not null,
    entity_type text,
    entity_id uuid,
    before jsonb,
    after jsonb,
    result text not null default 'SUCCESS' check (result in ('SUCCESS','FAILURE')),
    error_code text,
    duration_ms integer,
    created_at timestamptz not null default now()
);
create index audit_entity_idx on audit_logs(entity_type, entity_id, created_at desc);
create index audit_actor_idx on audit_logs(actor_type, actor_id, created_at desc);

create table import_jobs (
    id uuid primary key,
    source_name text not null,
    source_sha256 text not null,
    source_format text not null,
    status text not null default 'RUNNING'
        check (status in ('RUNNING','COMPLETED','FAILED','ROLLED_BACK')),
    students_imported integer not null default 0,
    sessions_imported integer not null default 0,
    questions_imported integer not null default 0,
    issues_count integer not null default 0,
    report jsonb not null default '{}',
    started_at timestamptz not null default now(),
    finished_at timestamptz
);

create table import_issues (
    id uuid primary key,
    import_job_id uuid not null references import_jobs(id) on delete cascade,
    severity text not null check (severity in ('INFO','WARN','ERROR')),
    entity_type text,
    source_ref text,
    message text not null,
    created_at timestamptz not null default now()
);
