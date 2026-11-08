-- Soft-delete duplicate normalized questions, keeping the most complete approved row.
with ranked as (
    select q.id,
           row_number() over (
               partition by q.normalized_hash
               order by case when q.review_status = 'APPROVED' then 0 else 1 end,
                        case when coalesce(a.standard_answer, '') <> '' then 0 else 1 end,
                        coalesce(length(a.analysis), 0) desc,
                        length(q.stem) desc,
                        q.created_at,
                        q.id
           ) as position
    from questions q
    left join question_answers a on a.question_id = q.id
    where q.deleted_at is null
)
update questions q
set deleted_at = now(), version = version + 1, updated_at = now()
from ranked r
where q.id = r.id and r.position > 1;

create unique index if not exists questions_normalized_unique_idx
    on questions(normalized_hash)
    where deleted_at is null;
