-- V001: extensions and shared helpers

create extension if not exists pg_trgm;

create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;
