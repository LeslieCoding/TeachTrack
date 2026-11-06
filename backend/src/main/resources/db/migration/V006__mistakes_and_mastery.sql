-- V006: mistakes and mastery

create table mistake_records (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    question_id uuid references questions(id),
    exam_paper_id uuid references exam_papers(id),
    knowledge_point_id uuid references knowledge_points(id),
    mistake_type text not null default 'UNKNOWN'
        check (mistake_type in
            ('KNOWLEDGE_GAP','CONCEPT_ERROR','FORMULA_ERROR','CALCULATION_ERROR','READING_ERROR',
             'UNIT_ERROR','LOGIC_ERROR','MEMORY_ERROR','TRANSFER_ERROR','CARELESS','UNKNOWN',
             'NEEDS_TEACHER_CONFIRMATION')),
    mistake_reason text not null default '',
    confidence numeric(4,3),
    first_seen_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    repeat_count integer not null default 1,
    status text not null default 'OPEN' check (status in ('OPEN','REVIEWING','RESOLVED','IGNORED')),
    import_batch_id uuid,
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index mistake_student_kp_idx on mistake_records(student_id, knowledge_point_id);
create index mistake_question_idx on mistake_records(question_id);

create table student_mastery (
    student_id uuid not null references students(id),
    knowledge_point_id uuid not null references knowledge_points(id),
    mastery_score numeric(5,2) not null default 0 check (mastery_score between 0 and 100),
    confidence numeric(4,3) not null default 0,
    practice_count integer not null default 0,
    correct_count integer not null default 0,
    wrong_count integer not null default 0,
    last_practiced_at timestamptz,
    last_wrong_at timestamptz,
    last_correct_at timestamptz,
    trend numeric(4,3) not null default 0,
    formula_version text not null default 'v1',
    version integer not null default 1,
    updated_at timestamptz not null default now(),
    primary key (student_id, knowledge_point_id)
);
create index mastery_kp_score_idx on student_mastery(knowledge_point_id, mastery_score);

create table mastery_evidence (
    id uuid primary key,
    student_id uuid not null references students(id),
    knowledge_point_id uuid not null references knowledge_points(id),
    source_type text not null check (source_type in ('PRACTICE','EXAM','RETEST','TEACHER')),
    source_id uuid,
    difficulty smallint,
    is_correct boolean,
    weight numeric(6,3) not null default 1,
    occurred_at timestamptz not null default now(),
    note text not null default ''
);
create index mastery_evidence_student_kp_idx on mastery_evidence(student_id, knowledge_point_id, occurred_at desc);
