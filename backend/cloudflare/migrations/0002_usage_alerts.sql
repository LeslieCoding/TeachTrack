create table usage_snapshots (
    id text primary key,
    metric text not null,
    period text not null,
    used real not null,
    quota real,
    unit text not null,
    ratio real,
    details text not null default '{}',
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index usage_snapshots_metric_idx on usage_snapshots(metric, created_at desc);

create table usage_alerts (
    id text primary key,
    metric text not null,
    period text not null,
    threshold real not null default 0.9,
    used real not null,
    quota real not null,
    ratio real not null,
    status text not null default 'ACTIVE' check (status in ('ACTIVE','ACKNOWLEDGED','RESOLVED')),
    first_triggered_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    last_notified_at text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create unique index usage_alerts_active_unique on usage_alerts(metric, period) where status in ('ACTIVE','ACKNOWLEDGED');
create index usage_alerts_status_idx on usage_alerts(status, updated_at desc);

create table notification_deliveries (
    id text primary key,
    alert_id text not null references usage_alerts(id) on delete cascade,
    channel text not null check (channel in ('APP','EMAIL')),
    destination text,
    status text not null check (status in ('SENT','SKIPPED','FAILED')),
    provider_message_id text,
    error text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
create index notification_deliveries_alert_idx on notification_deliveries(alert_id, created_at desc);
