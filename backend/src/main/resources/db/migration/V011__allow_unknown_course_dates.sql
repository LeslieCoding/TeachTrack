-- V011: historical course dates may be unknown; keep null instead of inventing a date

alter table course_sessions alter column date drop not null;
