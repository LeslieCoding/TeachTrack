-- V009: search indexes and updated_at triggers

create index if not exists students_name_trgm_idx on students using gin (display_name gin_trgm_ops);
create index if not exists knowledge_points_name_trgm_idx on knowledge_points using gin (name gin_trgm_ops);
create index if not exists questions_filter_idx on questions(subject_id, grade, question_type, difficulty)
    where deleted_at is null;
create index if not exists questions_review_idx on questions(review_status) where deleted_at is null;
create index if not exists questions_stem_hash_idx on questions(stem_hash);
create index if not exists questions_normalized_hash_idx on questions(normalized_hash);
create index if not exists questions_stem_trgm_idx on questions using gin (stem gin_trgm_ops);
create index if not exists question_knowledge_kp_idx on question_knowledge(knowledge_point_id);
create index if not exists student_answers_paper_idx on student_answers(exam_paper_id);

do $$
declare
    t text;
begin
    for t in
        select table_name
        from information_schema.columns
        where table_schema = 'public' and column_name = 'updated_at'
    loop
        execute format('drop trigger if exists trg_%1$s_updated_at on %1$I', t);
        execute format(
            'create trigger trg_%1$s_updated_at before update on %1$I '
            'for each row execute function set_updated_at()', t);
    end loop;
end;
$$;
