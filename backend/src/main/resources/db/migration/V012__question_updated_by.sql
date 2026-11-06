-- V012: question update audit field

alter table questions add column if not exists updated_by text;
