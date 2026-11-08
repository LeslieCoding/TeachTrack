-- Recompute normalized hashes with the same rule as QuestionService.normalizeStem:
-- lowercase, drop one leading question number ("12." / "（三）"), strip ASCII punctuation,
-- whitespace and the CJK punctuation set, keep digits so different values stay distinct.
-- Rows that now collapse to the same hash (same question printed under different numbers)
-- are soft-deleted, keeping the most complete approved row, then the unique index is rebuilt.

drop index if exists questions_normalized_unique_idx;

create temporary table normalized_rehash on commit drop as
select id,
       deleted_at,
       review_status,
       length(stem) as stem_length,
       coalesce((select length(a.analysis) from question_answers a where a.question_id = q.id), 0) as analysis_length,
       coalesce((select a.standard_answer from question_answers a where a.question_id = q.id), '') <> '' as has_answer,
       encode(sha256(convert_to(
           regexp_replace(
               regexp_replace(
                   lower(stem),
                   '^\s*[（(]?\s*(\d+|[一二三四五六七八九十]+)\s*[.、)）．]\s*',
                   ''
               ),
               E'[\\x21-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\x7e\\s，。；：！？、【】（）《》“”‘’]',
               '',
               'g'
           ),
           'UTF8'
       )), 'hex') as hash
from questions q
where deleted_at is null;

update questions q
set deleted_at = now(), version = version + 1, updated_at = now()
from (
    select id,
           row_number() over (
               partition by hash
               order by case when review_status = 'APPROVED' then 0 else 1 end,
                        case when has_answer then 0 else 1 end,
                        analysis_length desc,
                        stem_length desc,
                        id
           ) as position
    from normalized_rehash
) ranked
where q.id = ranked.id and ranked.position > 1;

update questions q
set normalized_hash = r.hash
from normalized_rehash r
where q.id = r.id
  and q.deleted_at is null
  and q.normalized_hash <> r.hash;

create unique index if not exists questions_normalized_unique_idx
    on questions(normalized_hash)
    where deleted_at is null;
