-- V005: exam papers and per-question analysis

create table exam_papers (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    subject_id uuid not null references subjects(id),
    exam_name text not null,
    exam_date date,
    full_score numeric(7,2),
    score numeric(7,2),
    storage_asset_id uuid references assets(id),
    source_type text not null default 'STUDENT_EXAM'
        check (source_type in
            ('SELF_CREATED','TEACHER_IMPORTED','STUDENT_EXAM','AUTHORIZED_BANK','AI_GENERATED','AI_VARIANT')),
    analysis_status text not null default 'UPLOADED'
        check (analysis_status in
            ('UPLOADED','PARSING','AI_PARSED','NEEDS_REVIEW','APPROVED','REJECTED','PARSE_FAILED')),
    parse_confidence numeric(4,3),
    ai_provider text not null default '',
    ai_model text not null default '',
    parse_error text not null default '',
    import_batch_id uuid,
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index exam_papers_student_date_idx on exam_papers(student_id, exam_date desc);

create table exam_questions (
    id uuid primary key,
    exam_paper_id uuid not null references exam_papers(id) on delete cascade,
    question_id uuid references questions(id),
    question_number text not null,
    question_type text,
    score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    sort_order integer not null default 0,
    source_page integer,
    source_bbox jsonb,
    unique (exam_paper_id, question_number)
);

create table student_answers (
    id uuid primary key,
    student_id uuid not null references students(id),
    exam_paper_id uuid not null references exam_papers(id) on delete cascade,
    exam_question_id uuid not null references exam_questions(id) on delete cascade,
    question_id uuid references questions(id),
    student_answer text not null default '',
    standard_answer text not null default '',
    score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    is_correct boolean,
    teacher_comment text not null default '',
    ai_comment text not null default '',
    confidence numeric(4,3),
    created_at timestamptz not null default now(),
    unique (exam_paper_id, exam_question_id)
);

create table exam_question_analyses (
    id uuid primary key,
    exam_paper_id uuid not null references exam_papers(id) on delete cascade,
    exam_question_id uuid not null references exam_questions(id) on delete cascade,
    student_id uuid not null references students(id),
    ability text not null default '',
    knowledge_summary text not null default '',
    mistake_type text not null default 'UNKNOWN'
        check (mistake_type in
            ('KNOWLEDGE_GAP','CONCEPT_ERROR','FORMULA_ERROR','CALCULATION_ERROR','READING_ERROR',
             'UNIT_ERROR','LOGIC_ERROR','MEMORY_ERROR','TRANSFER_ERROR','CARELESS','UNKNOWN',
             'NEEDS_TEACHER_CONFIRMATION')),
    mistake_reason text not null default '',
    confidence numeric(4,3),
    difficulty smallint,
    taught_before boolean not null default false,
    last_taught_at timestamptz,
    wrong_before boolean not null default false,
    repeat_count integer not null default 0,
    suggested_training text not null default '',
    potential_score_gain numeric(7,2) not null default 0,
    review_status text not null default 'AI_PARSED'
        check (review_status in ('AI_PARSED','NEEDS_REVIEW','APPROVED','REJECTED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (exam_question_id)
);

create table exam_analysis_reports (
    id uuid primary key,
    exam_paper_id uuid not null references exam_papers(id) on delete cascade,
    summary jsonb not null default '{}',
    by_question_type jsonb not null default '[]',
    by_knowledge jsonb not null default '[]',
    by_mistake_type jsonb not null default '[]',
    ai_provider text not null default '',
    ai_model text not null default '',
    prompt_version text not null default '',
    generated_at timestamptz not null default now(),
    review_status text not null default 'DRAFT'
);
