-- V002: identity, authorization, subjects, students

create table users (
    id uuid primary key,
    email text unique,
    display_name text not null,
    password_hash text,
    status text not null default 'ACTIVE' check (status in ('ACTIVE','DISABLED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role text not null check (role in ('OWNER','TEACHER','AI_READ','AI_WRITE_DRAFT','ADMIN')),
    primary key (user_id, role)
);

create table api_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    name text not null,
    token_prefix text not null,
    token_hash bytea not null unique,
    scopes text[] not null default '{}',
    rate_limit_per_min integer not null default 60,
    expires_at timestamptz,
    last_used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);
create index api_tokens_user_idx on api_tokens(user_id);

create table subjects (
    id uuid primary key,
    code text not null unique,
    name text not null,
    stage text not null default 'OTHER' check (stage in ('JUNIOR','SENIOR','OTHER')),
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

create table students (
    id uuid primary key,
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
    subject_id uuid references subjects(id),
    notes text not null default '',
    start_date date,
    import_batch_id uuid,
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index students_status_idx on students(status) where deleted_at is null;
create unique index students_legacy_uk on students(legacy_id) where legacy_id is not null;

create table student_aliases (
    id uuid primary key,
    student_id uuid not null references students(id) on delete cascade,
    alias text not null,
    alias_type text not null default 'NICKNAME'
        check (alias_type in ('NICKNAME','FORMER_NAME','MISSPELLING','TEACHING_NAME','OTHER')),
    source text not null default '',
    created_at timestamptz not null default now(),
    unique (student_id, alias)
);
create index student_aliases_alias_idx on student_aliases(alias);

create table student_profiles (
    student_id uuid primary key references students(id) on delete cascade,
    academic_profile jsonb not null default '{}',
    learning_characteristics jsonb not null default '{}',
    strengths text[] not null default '{}',
    weaknesses text[] not null default '{}',
    risk_flags text[] not null default '{}',
    special_rules text[] not null default '{}',
    updated_at timestamptz not null default now()
);

create table parent_contacts (
    id uuid primary key,
    legacy_id text unique,
    student_id uuid not null references students(id) on delete cascade,
    name text not null default '',
    phone text not null default '',
    relation text not null default '',
    is_primary boolean not null default false,
    notes text not null default '',
    import_batch_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index parent_contacts_student_idx on parent_contacts(student_id);
