-- V007: practice sets, attempts, results

create table practice_sets (
    id uuid primary key,
    student_id uuid not null references students(id),
    subject_id uuid not null references subjects(id),
    mode text not null check (mode in
        ('BASIC','ERROR_FOCUS','TOPIC','COMPREHENSIVE','PRE_EXAM','RETEST')),
    title text not null,
    strategy jsonb not null default '{}',
    total_score numeric(7,2) not null default 0,
    estimated_minutes integer not null default 0,
    status text not null default 'DRAFT'
        check (status in ('DRAFT','GENERATED','TEACHER_REVIEWED','APPROVED','EXPORTED','COMPLETED','CANCELLED')),
    generated_by text not null default 'TEACHER',
    ai_provider text not null default '',
    ai_model text not null default '',
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index practice_sets_student_idx on practice_sets(student_id, created_at desc);

create table practice_items (
    id uuid primary key,
    practice_set_id uuid not null references practice_sets(id) on delete cascade,
    question_id uuid not null references questions(id),
    source_kind text not null check (source_kind in ('BANK','MISTAKE_VARIANT','SIMILAR','AI_ORIGINAL')),
    selection_reason text not null default '',
    score numeric(7,2) not null default 0,
    sort_order integer not null default 0,
    unique (practice_set_id, sort_order)
);

create table practice_attempts (
    id uuid primary key,
    practice_set_id uuid not null references practice_sets(id),
    student_id uuid not null references students(id),
    submitted_at timestamptz not null default now(),
    total_score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    created_at timestamptz not null default now()
);

create table practice_item_results (
    id uuid primary key,
    attempt_id uuid not null references practice_attempts(id) on delete cascade,
    practice_item_id uuid not null references practice_items(id),
    student_answer text not null default '',
    score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    is_correct boolean,
    ai_comment text not null default '',
    teacher_comment text not null default '',
    created_at timestamptz not null default now(),
    unique (attempt_id, practice_item_id)
);
