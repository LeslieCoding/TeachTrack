-- V013: idempotency for sync operations

create table sync_operations (
    id uuid primary key,
    operation_id text not null unique,
    device_id uuid,
    entity_type text not null,
    entity_id uuid not null,
    base_version integer not null default 0,
    result_version integer,
    status text not null check (status in ('APPLIED','CONFLICT','REJECTED')),
    created_at timestamptz not null default now()
);
create index sync_operations_entity_idx on sync_operations(entity_type, entity_id);
