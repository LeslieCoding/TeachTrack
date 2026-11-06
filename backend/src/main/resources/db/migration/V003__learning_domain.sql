-- V003: learning domain (courses, knowledge, legacy scores/homework/etc.)

create table course_sessions (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    date date not null,
    start_time text not null default '',
    end_time text not null default '',
    subject_id uuid references subjects(id),
    lesson_title text not null default '',
    lesson_content text not null default '',
    performance text not null default '',
    problems text not null default '',
    homework_summary text not null default '',
    next_lesson_plan text not null default '',
    teacher_notes text not null default '',
    learning_status text not null default '',
    source text not null default 'LOCAL',
    import_batch_id uuid,
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index course_sessions_student_date_idx on course_sessions(student_id, date desc);

create table knowledge_points (
    id uuid primary key,
    legacy_id text unique,
    parent_id uuid references knowledge_points(id),
    subject_id uuid not null references subjects(id),
    grade text not null default '',
    textbook_version text not null default '',
    chapter text not null default '',
    section text not null default '',
    name text not null,
    code text,
    description text not null default '',
    sort_order integer not null default 0,
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (subject_id, code)
);
create index knowledge_points_parent_idx on knowledge_points(parent_id);
create index knowledge_points_subject_grade_idx on knowledge_points(subject_id, grade);

create table student_knowledge_progress_legacy (
    student_id uuid not null references students(id) on delete cascade,
    knowledge_point_id uuid not null references knowledge_points(id) on delete cascade,
    level smallint not null check (level between 0 and 4),
    last_updated timestamptz,
    notes text not null default '',
    primary key (student_id, knowledge_point_id)
);

create table knowledge_events (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id) on delete cascade,
    knowledge_point_id uuid not null references knowledge_points(id) on delete cascade,
    level smallint not null,
    occurred_at timestamptz not null,
    notes text not null default ''
);
create index knowledge_events_student_idx on knowledge_events(student_id, occurred_at desc);

create table student_scores (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    exam_id uuid,
    exam_name text not null default '',
    exam_date date,
    exam_type text not null default '',
    subject_id uuid references subjects(id),
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
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index student_scores_student_idx on student_scores(student_id);

create table score_items (
    id uuid primary key,
    legacy_id text unique,
    score_id uuid not null references student_scores(id) on delete cascade,
    category text not null default '',
    lost_score numeric(7,2) not null default 0,
    knowledge_point_id uuid references knowledge_points(id),
    notes text not null default ''
);
create index score_items_score_idx on score_items(score_id);

create table homework (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    course_id uuid references course_sessions(id),
    name text not null,
    assigned_date date,
    due_date date,
    content text not null default '',
    status text not null default '未完成',
    teacher_comment text not null default '',
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index homework_student_idx on homework(student_id);

create table communications (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    date date,
    contact text not null default '',
    method text not null default '微信',
    summary text not null default '',
    parent_feedback text not null default '',
    follow_up text not null default '',
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index communications_student_idx on communications(student_id);

create table lesson_plans (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    planned_date date,
    chapter text not null default '',
    knowledge_points text not null default '',
    focus text not null default '',
    difficulty text not null default '',
    review text not null default '',
    new_content text not null default '',
    homework text not null default '',
    notes text not null default '',
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index lesson_plans_student_idx on lesson_plans(student_id);

create table tasks (
    id uuid primary key,
    legacy_id text unique,
    title text not null,
    student_id uuid references students(id),
    type text not null default '其他',
    due_date date,
    priority text not null default '普通',
    status text not null default '待处理',
    notes text not null default '',
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index tasks_status_idx on tasks(status);

create table templates (
    id uuid primary key,
    legacy_id text unique,
    name text not null,
    type text not null default '课后反馈',
    content text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table tags (
    id uuid primary key,
    legacy_id text unique,
    name text not null unique,
    color text not null default '#176B57',
    created_at timestamptz not null default now()
);

create table student_tags (
    student_id uuid not null references students(id) on delete cascade,
    tag_id uuid not null references tags(id) on delete cascade,
    primary key (student_id, tag_id)
);

create table historical_learning_records (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    record_date date,
    date_accuracy text not null default 'UNKNOWN',
    subject_id uuid references subjects(id),
    title text not null,
    content text not null default '',
    source text not null default '历史教学资料',
    import_batch_id uuid,
    created_at timestamptz not null default now()
);
create index historical_records_student_idx on historical_learning_records(student_id);

create table student_progress (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id),
    date date,
    date_accuracy text not null default 'UNKNOWN',
    category text not null,
    title text not null,
    detail text not null default '',
    source text not null default '历史教学资料',
    import_batch_id uuid,
    created_at timestamptz not null default now()
);
create index student_progress_student_idx on student_progress(student_id);

create table unresolved_historical_records (
    id uuid primary key,
    legacy_id text unique,
    record_date date,
    date_accuracy text not null default 'UNKNOWN',
    title text not null,
    content text not null default '',
    source text not null default '历史教学资料',
    import_batch_id uuid,
    created_at timestamptz not null default now()
);
