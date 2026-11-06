-- V004: question bank

create table questions (
    id uuid primary key,
    legacy_id text unique,
    subject_id uuid not null references subjects(id),
    grade text not null default '',
    textbook_version text not null default '',
    question_type text not null check (question_type in
        ('SINGLE_CHOICE','MULTI_CHOICE','FILL_BLANK','EXPERIMENT','CALCULATION','SHORT_ANSWER','ESSAY','OTHER')),
    difficulty smallint not null default 3 check (difficulty between 1 and 5),
    stem text not null,
    stem_hash text not null,
    normalized_hash text not null,
    source_type text not null check (source_type in
        ('SELF_CREATED','TEACHER_IMPORTED','STUDENT_EXAM','AUTHORIZED_BANK','AI_GENERATED','AI_VARIANT')),
    source_name text not null default '',
    copyright_status text not null default 'PERSONAL_USE'
        check (copyright_status in ('PERSONAL_USE','AUTHORIZED','PUBLIC_DOMAIN','UNKNOWN')),
    usage_scope text not null default 'PRIVATE_TEACHING'
        check (usage_scope in ('PRIVATE_TEACHING','CLASSROOM','PUBLIC')),
    review_status text not null default 'DRAFT'
        check (review_status in ('DRAFT','AI_PARSED','NEEDS_REVIEW','NEEDS_TEACHER_REVIEW','APPROVED','REJECTED')),
    quality_score numeric(4,2),
    ability_tags text[] not null default '{}',
    created_by text,
    import_batch_id uuid,
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table question_versions (
    id uuid primary key,
    question_id uuid not null references questions(id) on delete cascade,
    version integer not null,
    snapshot jsonb not null,
    change_note text not null default '',
    created_by text,
    created_at timestamptz not null default now(),
    unique (question_id, version)
);

create table question_options (
    id uuid primary key,
    question_id uuid not null references questions(id) on delete cascade,
    option_key text not null,
    content text not null,
    sort_order integer not null default 0,
    unique (question_id, option_key)
);

create table question_answers (
    question_id uuid primary key references questions(id) on delete cascade,
    standard_answer text not null,
    analysis text not null default '',
    scoring_points text not null default '',
    solution_steps text not null default '',
    updated_at timestamptz not null default now()
);

create table question_knowledge (
    question_id uuid not null references questions(id) on delete cascade,
    knowledge_point_id uuid not null references knowledge_points(id),
    importance text not null default 'PRIMARY'
        check (importance in ('PRIMARY','SECONDARY','RELATED')),
    primary key (question_id, knowledge_point_id)
);

create table assets (
    id uuid primary key,
    owner_type text not null default 'GENERAL',
    owner_id uuid,
    storage_provider text not null default 's3',
    storage_key text not null,
    mime_type text not null,
    size_bytes bigint not null default 0,
    sha256 text not null,
    visibility text not null default 'PRIVATE' check (visibility = 'PRIVATE'),
    status text not null default 'PENDING'
        check (status in ('PENDING','READY','FAILED','DELETED')),
    width integer,
    height integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (storage_provider, storage_key)
);
create index assets_sha256_idx on assets(sha256);

create table question_assets (
    id uuid primary key,
    question_id uuid not null references questions(id) on delete cascade,
    asset_id uuid not null references assets(id),
    asset_type text not null check (asset_type in
        ('IMAGE','DIAGRAM','GRAPH','FORMULA','PDF_PAGE','OTHER')),
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    unique (question_id, asset_id)
);
