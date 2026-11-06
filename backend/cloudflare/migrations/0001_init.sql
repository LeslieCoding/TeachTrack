-- TeachTrack D1/SQLite schema, generated from the final PostgreSQL migrations.
-- UUIDs, dates, timestamps and JSON/arrays are stored as TEXT; booleans as INTEGER.
-- API property names and values remain compatible with the Spring Boot backend.
-- Regenerate with: python tools/build-schema.py

-- V002__identity_and_students.sql
-- V002: identity, authorization, subjects, students

create table users (
    id text primary key,
    email text unique,
    display_name text not null,
    password_hash text,
    status text not null default 'ACTIVE' check (status in ('ACTIVE','DISABLED')),
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table user_roles (
    user_id text not null references users(id) on delete cascade,
    role text not null check (role in ('OWNER','TEACHER','AI_READ','AI_WRITE_DRAFT','ADMIN')),
    primary key (user_id, role)
);

create table api_tokens (
    id text primary key,
    user_id text not null references users(id) on delete cascade,
    name text not null,
    token_prefix text not null,
    token_hash blob not null unique,
    scopes text not null default '{}',
    rate_limit_per_min integer not null default 60,
    expires_at text,
    last_used_at text,
    revoked_at text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index api_tokens_user_idx on api_tokens(user_id);

create table subjects (
    id text primary key,
    code text not null unique,
    name text not null,
    stage text not null default 'OTHER' check (stage in ('JUNIOR','SENIOR','OTHER')),
    sort_order integer not null default 0,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table students (
    id text primary key,
    legacy_id text unique,
    full_name text,
    display_name text not null,
    status text not null default 'ACTIVE'
        check (status in ('UNKNOWN','ACTIVE','PAUSED','ENDED','ARCHIVED')),
    gender text,
    current_grade text,
    grade_at_record text,
    name_completeness text not null default 'CONFIRMED'
        check (name_completeness in ('CONFIRMED','PARTIAL','UNKNOWN')),
    school text not null default '',
    class_name text not null default '',
    subject_id text references subjects(id),
    notes text not null default '',
    start_date text,
    import_batch_id text,
    version integer not null default 1,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index students_status_idx on students(status) where deleted_at is null;
create unique index students_legacy_uk on students(legacy_id) where legacy_id is not null;

create table student_aliases (
    id text primary key,
    student_id text not null references students(id) on delete cascade,
    alias text not null,
    alias_type text not null default 'NICKNAME'
        check (alias_type in ('NICKNAME','FORMER_NAME','MISSPELLING','TEACHING_NAME','OTHER')),
    source text not null default '',
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (student_id, alias)
);
create index student_aliases_alias_idx on student_aliases(alias);

create table student_profiles (
    student_id text primary key references students(id) on delete cascade,
    academic_profile text not null default '{}',
    learning_characteristics text not null default '{}',
    strengths text not null default '{}',
    weaknesses text not null default '{}',
    risk_flags text not null default '{}',
    special_rules text not null default '{}',
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table parent_contacts (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id) on delete cascade,
    name text not null default '',
    phone text not null default '',
    relation text not null default '',
    is_primary integer not null default 0,
    notes text not null default '',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index parent_contacts_student_idx on parent_contacts(student_id);


-- V003__learning_domain.sql
-- V003: learning domain (courses, knowledge, legacy scores/homework/etc.)

create table course_sessions (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    date text,
    start_time text not null default '',
    end_time text not null default '',
    subject_id text references subjects(id),
    lesson_title text not null default '',
    lesson_content text not null default '',
    performance text not null default '',
    problems text not null default '',
    homework_summary text not null default '',
    next_lesson_plan text not null default '',
    teacher_notes text not null default '',
    learning_status text not null default '',
    source text not null default 'LOCAL',
    import_batch_id text,
    version integer not null default 1,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index course_sessions_student_date_idx on course_sessions(student_id, date desc);

create table knowledge_points (
    id text primary key,
    legacy_id text unique,
    parent_id text references knowledge_points(id),
    subject_id text not null references subjects(id),
    grade text not null default '',
    textbook_version text not null default '',
    chapter text not null default '',
    section text not null default '',
    name text not null,
    code text,
    description text not null default '',
    sort_order integer not null default 0,
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text,
    unique (subject_id, code)
);
create index knowledge_points_parent_idx on knowledge_points(parent_id);
create index knowledge_points_subject_grade_idx on knowledge_points(subject_id, grade);

create table student_knowledge_progress_legacy (
    student_id text not null references students(id) on delete cascade,
    knowledge_point_id text not null references knowledge_points(id) on delete cascade,
    level integer not null check (level between 0 and 4),
    last_updated text,
    notes text not null default '',
    primary key (student_id, knowledge_point_id)
);

create table knowledge_events (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id) on delete cascade,
    knowledge_point_id text not null references knowledge_points(id) on delete cascade,
    level integer not null,
    occurred_at text not null,
    notes text not null default ''
);
create index knowledge_events_student_idx on knowledge_events(student_id, occurred_at desc);

create table student_scores (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    exam_id text,
    exam_name text not null default '',
    exam_date text,
    exam_type text not null default '',
    subject_id text references subjects(id),
    score numeric(7,2) not null,
    full_score numeric(7,2) not null,
    class_rank integer,
    grade_rank integer,
    class_size integer,
    grade_size integer,
    notes text not null default '',
    score_label text not null default '',
    full_score_label text not null default '',
    score_accuracy text not null default 'EXACT',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index student_scores_student_idx on student_scores(student_id);

create table score_items (
    id text primary key,
    legacy_id text unique,
    score_id text not null references student_scores(id) on delete cascade,
    category text not null default '',
    lost_score numeric(7,2) not null default 0,
    knowledge_point_id text references knowledge_points(id),
    notes text not null default ''
);
create index score_items_score_idx on score_items(score_id);

create table homework (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    course_id text references course_sessions(id),
    name text not null,
    assigned_date text,
    due_date text,
    content text not null default '',
    status text not null default '未完成',
    teacher_comment text not null default '',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index homework_student_idx on homework(student_id);

create table communications (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    date text,
    contact text not null default '',
    method text not null default '微信',
    summary text not null default '',
    parent_feedback text not null default '',
    follow_up text not null default '',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index communications_student_idx on communications(student_id);

create table lesson_plans (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    planned_date text,
    chapter text not null default '',
    knowledge_points text not null default '',
    focus text not null default '',
    difficulty text not null default '',
    review text not null default '',
    new_content text not null default '',
    homework text not null default '',
    notes text not null default '',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index lesson_plans_student_idx on lesson_plans(student_id);

create table tasks (
    id text primary key,
    legacy_id text unique,
    title text not null,
    student_id text references students(id),
    type text not null default '其他',
    due_date text,
    priority text not null default '普通',
    status text not null default '待处理',
    notes text not null default '',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index tasks_status_idx on tasks(status);

create table templates (
    id text primary key,
    legacy_id text unique,
    name text not null,
    type text not null default '课后反馈',
    content text not null default '',
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table tags (
    id text primary key,
    legacy_id text unique,
    name text not null unique,
    color text not null default '#176B57',
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table student_tags (
    student_id text not null references students(id) on delete cascade,
    tag_id text not null references tags(id) on delete cascade,
    primary key (student_id, tag_id)
);

create table historical_learning_records (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    record_date text,
    date_accuracy text not null default 'UNKNOWN',
    subject_id text references subjects(id),
    title text not null,
    content text not null default '',
    source text not null default '历史教学资料',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index historical_records_student_idx on historical_learning_records(student_id);

create table student_progress (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    date text,
    date_accuracy text not null default 'UNKNOWN',
    category text not null,
    title text not null,
    detail text not null default '',
    source text not null default '历史教学资料',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index student_progress_student_idx on student_progress(student_id);

create table unresolved_historical_records (
    id text primary key,
    legacy_id text unique,
    record_date text,
    date_accuracy text not null default 'UNKNOWN',
    title text not null,
    content text not null default '',
    source text not null default '历史教学资料',
    import_batch_id text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);


-- V004__question_bank.sql
-- V004: question bank

create table questions (
    id text primary key,
    legacy_id text unique,
    subject_id text not null references subjects(id),
    grade text not null default '',
    textbook_version text not null default '',
    question_type text not null check (question_type in
        ('SINGLE_CHOICE','MULTI_CHOICE','FILL_BLANK','EXPERIMENT','CALCULATION','SHORT_ANSWER','ESSAY','OTHER')),
    difficulty integer not null default 3 check (difficulty between 1 and 5),
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
    ability_tags text not null default '{}',
    created_by text,
    import_batch_id text,
    version integer not null default 1,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_by text,
    deleted_at text
);

create table question_versions (
    id text primary key,
    question_id text not null references questions(id) on delete cascade,
    version integer not null,
    snapshot text not null,
    change_note text not null default '',
    created_by text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (question_id, version)
);

create table question_options (
    id text primary key,
    question_id text not null references questions(id) on delete cascade,
    option_key text not null,
    content text not null,
    sort_order integer not null default 0,
    unique (question_id, option_key)
);

create table question_answers (
    question_id text primary key references questions(id) on delete cascade,
    standard_answer text not null,
    analysis text not null default '',
    scoring_points text not null default '',
    solution_steps text not null default '',
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table question_knowledge (
    question_id text not null references questions(id) on delete cascade,
    knowledge_point_id text not null references knowledge_points(id),
    importance text not null default 'PRIMARY'
        check (importance in ('PRIMARY','SECONDARY','RELATED')),
    primary key (question_id, knowledge_point_id)
);

create table assets (
    id text primary key,
    owner_type text not null default 'GENERAL',
    owner_id text,
    storage_provider text not null default 's3',
    storage_key text not null,
    mime_type text not null,
    size_bytes integer not null default 0,
    sha256 text not null,
    visibility text not null default 'PRIVATE' check (visibility = 'PRIVATE'),
    status text not null default 'PENDING'
        check (status in ('PENDING','READY','FAILED','DELETED')),
    width integer,
    height integer,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (storage_provider, storage_key)
);
create index assets_sha256_idx on assets(sha256);

create table question_assets (
    id text primary key,
    question_id text not null references questions(id) on delete cascade,
    asset_id text not null references assets(id),
    asset_type text not null check (asset_type in
        ('IMAGE','DIAGRAM','GRAPH','FORMULA','PDF_PAGE','OTHER')),
    sort_order integer not null default 0,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (question_id, asset_id)
);


-- V005__exam_analysis.sql
-- V005: exam papers and per-question analysis

create table exam_papers (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    subject_id text not null references subjects(id),
    exam_name text not null,
    exam_date text,
    full_score numeric(7,2),
    score numeric(7,2),
    storage_asset_id text references assets(id),
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
    import_batch_id text,
    version integer not null default 1,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index exam_papers_student_date_idx on exam_papers(student_id, exam_date desc);

create table exam_questions (
    id text primary key,
    exam_paper_id text not null references exam_papers(id) on delete cascade,
    question_id text references questions(id),
    question_number text not null,
    question_type text,
    score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    sort_order integer not null default 0,
    source_page integer,
    source_bbox text,
    stem text not null default '',
    standard_answer text not null default '',
    unique (exam_paper_id, question_number)
);

create table student_answers (
    id text primary key,
    student_id text not null references students(id),
    exam_paper_id text not null references exam_papers(id) on delete cascade,
    exam_question_id text not null references exam_questions(id) on delete cascade,
    question_id text references questions(id),
    student_answer text not null default '',
    standard_answer text not null default '',
    score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    is_correct integer,
    teacher_comment text not null default '',
    ai_comment text not null default '',
    confidence numeric(4,3),
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (exam_paper_id, exam_question_id)
);

create table exam_question_analyses (
    id text primary key,
    exam_paper_id text not null references exam_papers(id) on delete cascade,
    exam_question_id text not null references exam_questions(id) on delete cascade,
    student_id text not null references students(id),
    ability text not null default '',
    knowledge_summary text not null default '',
    mistake_type text not null default 'UNKNOWN'
        check (mistake_type in
            ('KNOWLEDGE_GAP','CONCEPT_ERROR','FORMULA_ERROR','CALCULATION_ERROR','READING_ERROR',
             'UNIT_ERROR','LOGIC_ERROR','MEMORY_ERROR','TRANSFER_ERROR','CARELESS','UNKNOWN',
             'NEEDS_TEACHER_CONFIRMATION')),
    mistake_reason text not null default '',
    confidence numeric(4,3),
    difficulty integer,
    taught_before integer not null default 0,
    last_taught_at text,
    wrong_before integer not null default 0,
    repeat_count integer not null default 0,
    suggested_training text not null default '',
    potential_score_gain numeric(7,2) not null default 0,
    review_status text not null default 'AI_PARSED'
        check (review_status in ('AI_PARSED','NEEDS_REVIEW','APPROVED','REJECTED')),
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (exam_question_id)
);

create table exam_analysis_reports (
    id text primary key,
    exam_paper_id text not null references exam_papers(id) on delete cascade,
    summary text not null default '{}',
    by_question_type text not null default '[]',
    by_knowledge text not null default '[]',
    by_mistake_type text not null default '[]',
    ai_provider text not null default '',
    ai_model text not null default '',
    prompt_version text not null default '',
    generated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    review_status text not null default 'DRAFT'
);


-- V006__mistakes_and_mastery.sql
-- V006: mistakes and mastery

create table mistake_records (
    id text primary key,
    legacy_id text unique,
    student_id text not null references students(id),
    question_id text references questions(id),
    exam_paper_id text references exam_papers(id),
    knowledge_point_id text references knowledge_points(id),
    mistake_type text not null default 'UNKNOWN'
        check (mistake_type in
            ('KNOWLEDGE_GAP','CONCEPT_ERROR','FORMULA_ERROR','CALCULATION_ERROR','READING_ERROR',
             'UNIT_ERROR','LOGIC_ERROR','MEMORY_ERROR','TRANSFER_ERROR','CARELESS','UNKNOWN',
             'NEEDS_TEACHER_CONFIRMATION')),
    mistake_reason text not null default '',
    confidence numeric(4,3),
    first_seen_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    last_seen_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    repeat_count integer not null default 1,
    status text not null default 'OPEN' check (status in ('OPEN','REVIEWING','RESOLVED','IGNORED')),
    import_batch_id text,
    version integer not null default 1,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index mistake_student_kp_idx on mistake_records(student_id, knowledge_point_id);
create index mistake_question_idx on mistake_records(question_id);

create table student_mastery (
    student_id text not null references students(id),
    knowledge_point_id text not null references knowledge_points(id),
    mastery_score numeric(5,2) not null default 0 check (mastery_score between 0 and 100),
    confidence numeric(4,3) not null default 0,
    practice_count integer not null default 0,
    correct_count integer not null default 0,
    wrong_count integer not null default 0,
    last_practiced_at text,
    last_wrong_at text,
    last_correct_at text,
    trend numeric(4,3) not null default 0,
    formula_version text not null default 'v1',
    version integer not null default 1,
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    primary key (student_id, knowledge_point_id)
);
create index mastery_kp_score_idx on student_mastery(knowledge_point_id, mastery_score);

create table mastery_evidence (
    id text primary key,
    student_id text not null references students(id),
    knowledge_point_id text not null references knowledge_points(id),
    source_type text not null check (source_type in ('PRACTICE','EXAM','RETEST','TEACHER')),
    source_id text,
    difficulty integer,
    is_correct integer,
    weight numeric(6,3) not null default 1,
    occurred_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    note text not null default ''
);
create index mastery_evidence_student_kp_idx on mastery_evidence(student_id, knowledge_point_id, occurred_at desc);


-- V007__practice.sql
-- V007: practice sets, attempts, results

create table practice_sets (
    id text primary key,
    student_id text not null references students(id),
    subject_id text not null references subjects(id),
    mode text not null check (mode in
        ('BASIC','ERROR_FOCUS','TOPIC','COMPREHENSIVE','PRE_EXAM','RETEST')),
    title text not null,
    strategy text not null default '{}',
    total_score numeric(7,2) not null default 0,
    estimated_minutes integer not null default 0,
    status text not null default 'DRAFT'
        check (status in ('DRAFT','GENERATED','TEACHER_REVIEWED','APPROVED','EXPORTED','COMPLETED','CANCELLED')),
    generated_by text not null default 'TEACHER',
    ai_provider text not null default '',
    ai_model text not null default '',
    version integer not null default 1,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    deleted_at text
);
create index practice_sets_student_idx on practice_sets(student_id, created_at desc);

create table practice_items (
    id text primary key,
    practice_set_id text not null references practice_sets(id) on delete cascade,
    question_id text not null references questions(id),
    source_kind text not null check (source_kind in ('BANK','MISTAKE_VARIANT','SIMILAR','AI_ORIGINAL')),
    selection_reason text not null default '',
    score numeric(7,2) not null default 0,
    sort_order integer not null default 0,
    unique (practice_set_id, sort_order)
);

create table practice_attempts (
    id text primary key,
    practice_set_id text not null references practice_sets(id),
    student_id text not null references students(id),
    submitted_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    total_score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table practice_item_results (
    id text primary key,
    attempt_id text not null references practice_attempts(id) on delete cascade,
    practice_item_id text not null references practice_items(id),
    student_answer text not null default '',
    score numeric(7,2) not null default 0,
    full_score numeric(7,2) not null default 0,
    is_correct integer,
    ai_comment text not null default '',
    teacher_comment text not null default '',
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (attempt_id, practice_item_id)
);


-- V008__sync_and_audit.sql
-- V008: sync, audit, imports

create table sync_devices (
    id text primary key,
    user_id text not null references users(id),
    device_name text not null,
    platform text not null default 'android',
    app_version text not null default '',
    last_seen_at text,
    last_cursor integer not null default 0,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table sync_changes (
    seq integer primary key,
    entity_type text not null,
    entity_id text not null,
    operation text not null check (operation in ('UPSERT','DELETE')),
    version integer not null,
    device_id text,
    payload text,
    changed_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index sync_changes_entity_idx on sync_changes(entity_type, entity_id, seq desc);
create index sync_changes_seq_idx on sync_changes(seq);

create table audit_logs (
    id text primary key,
    actor_type text not null check (actor_type in ('USER','AI','SYSTEM')),
    actor_id text,
    token_id text,
    provider text,
    model text,
    request_id text,
    device_id text,
    action text not null,
    entity_type text,
    entity_id text,
    before text,
    after text,
    result text not null default 'SUCCESS' check (result in ('SUCCESS','FAILURE')),
    error_code text,
    duration_ms integer,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index audit_entity_idx on audit_logs(entity_type, entity_id, created_at desc);
create index audit_actor_idx on audit_logs(actor_type, actor_id, created_at desc);

create table import_jobs (
    id text primary key,
    source_name text not null,
    source_sha256 text not null,
    source_format text not null,
    status text not null default 'RUNNING'
        check (status in ('RUNNING','COMPLETED','FAILED','ROLLED_BACK')),
    students_imported integer not null default 0,
    sessions_imported integer not null default 0,
    questions_imported integer not null default 0,
    issues_count integer not null default 0,
    report text not null default '{}',
    started_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    finished_at text
);

create table import_issues (
    id text primary key,
    import_job_id text not null references import_jobs(id) on delete cascade,
    severity text not null check (severity in ('INFO','WARN','ERROR')),
    entity_type text,
    source_ref text,
    message text not null,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);


-- V010__subject_seed.sql
-- V010: reference subjects (deterministic UUIDv5 values, namespace 6f1c2f8a-4c1e-5b7a-9d3e-7a2b1c9d0e4f)

insert into subjects (id, code, name, stage, sort_order) values
    ('5681f185-310d-52e4-964c-be9d8cf1f5a6', 'JHS_SCIENCE', '初中科学', 'JUNIOR', 10),
    ('cd33aea7-be41-5174-bc9f-b298fe949bf8', 'SHS_PHYSICS', '高中物理', 'SENIOR', 20),
    ('8bf973fd-1241-571d-9bdb-b1b20815ad15', 'SHS_BIOLOGY', '高中生物', 'SENIOR', 30),
    ('3f472a2b-acc2-52f1-9dfa-f46bf278294a', 'OTHER', '其他', 'OTHER', 90)
on conflict (code) do nothing;


-- V013__sync_operations.sql
-- V013: idempotency for sync operations

create table sync_operations (
    id text primary key,
    operation_id text not null unique,
    device_id text,
    entity_type text not null,
    entity_id text not null,
    base_version integer not null default 0,
    result_version integer,
    status text not null check (status in ('APPLIED','CONFLICT','REJECTED')),
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index sync_operations_entity_idx on sync_operations(entity_type, entity_id);


-- Portable indexes from V009 (PostgreSQL GIN/trigram indexes are not used by D1)
create index if not exists questions_filter_idx on questions(subject_id, grade, question_type, difficulty)
    where deleted_at is null;
create index if not exists questions_review_idx on questions(review_status) where deleted_at is null;
create index if not exists questions_stem_hash_idx on questions(stem_hash);
create index if not exists questions_normalized_hash_idx on questions(normalized_hash);
create index if not exists question_knowledge_kp_idx on question_knowledge(knowledge_point_id);
create index if not exists student_answers_paper_idx on student_answers(exam_paper_id);


-- updated_at maintenance
create trigger trg_users_updated_at after update on users
for each row when new.updated_at = old.updated_at
begin
    update users set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_students_updated_at after update on students
for each row when new.updated_at = old.updated_at
begin
    update students set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_student_profiles_updated_at after update on student_profiles
for each row when new.updated_at = old.updated_at
begin
    update student_profiles set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_parent_contacts_updated_at after update on parent_contacts
for each row when new.updated_at = old.updated_at
begin
    update parent_contacts set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_course_sessions_updated_at after update on course_sessions
for each row when new.updated_at = old.updated_at
begin
    update course_sessions set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_knowledge_points_updated_at after update on knowledge_points
for each row when new.updated_at = old.updated_at
begin
    update knowledge_points set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_student_scores_updated_at after update on student_scores
for each row when new.updated_at = old.updated_at
begin
    update student_scores set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_homework_updated_at after update on homework
for each row when new.updated_at = old.updated_at
begin
    update homework set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_communications_updated_at after update on communications
for each row when new.updated_at = old.updated_at
begin
    update communications set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_lesson_plans_updated_at after update on lesson_plans
for each row when new.updated_at = old.updated_at
begin
    update lesson_plans set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_tasks_updated_at after update on tasks
for each row when new.updated_at = old.updated_at
begin
    update tasks set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_templates_updated_at after update on templates
for each row when new.updated_at = old.updated_at
begin
    update templates set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_questions_updated_at after update on questions
for each row when new.updated_at = old.updated_at
begin
    update questions set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_question_answers_updated_at after update on question_answers
for each row when new.updated_at = old.updated_at
begin
    update question_answers set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_assets_updated_at after update on assets
for each row when new.updated_at = old.updated_at
begin
    update assets set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_exam_papers_updated_at after update on exam_papers
for each row when new.updated_at = old.updated_at
begin
    update exam_papers set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_exam_question_analyses_updated_at after update on exam_question_analyses
for each row when new.updated_at = old.updated_at
begin
    update exam_question_analyses set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_mistake_records_updated_at after update on mistake_records
for each row when new.updated_at = old.updated_at
begin
    update mistake_records set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_student_mastery_updated_at after update on student_mastery
for each row when new.updated_at = old.updated_at
begin
    update student_mastery set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;

create trigger trg_practice_sets_updated_at after update on practice_sets
for each row when new.updated_at = old.updated_at
begin
    update practice_sets set updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where rowid = new.rowid;
end;
