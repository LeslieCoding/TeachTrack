-- V014: keep parsed stem/answer on the exam question before it enters the bank

alter table exam_questions add column if not exists stem text not null default '';
alter table exam_questions add column if not exists standard_answer text not null default '';
