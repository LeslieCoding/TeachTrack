-- V010: reference subjects (deterministic UUIDv5 values, namespace 6f1c2f8a-4c1e-5b7a-9d3e-7a2b1c9d0e4f)

insert into subjects (id, code, name, stage, sort_order) values
    ('5681f185-310d-52e4-964c-be9d8cf1f5a6', 'JHS_SCIENCE', '初中科学', 'JUNIOR', 10),
    ('cd33aea7-be41-5174-bc9f-b298fe949bf8', 'SHS_PHYSICS', '高中物理', 'SENIOR', 20),
    ('8bf973fd-1241-571d-9bdb-b1b20815ad15', 'SHS_BIOLOGY', '高中生物', 'SENIOR', 30),
    ('3f472a2b-acc2-52f1-9dfa-f46bf278294a', 'OTHER', '其他', 'OTHER', 90)
on conflict (code) do nothing;
