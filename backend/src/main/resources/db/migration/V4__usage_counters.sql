-- Privacy-friendly usage analytics: one counter per (campus day, event). No event log, no
-- identifiers, no timestamps finer than a day. This is the entire analytics schema.
create table usage_daily (
    day   date   not null,
    event text   not null,
    count bigint not null check (count >= 0),
    primary key (day, event)
);
-- The primary key index (day, event) also serves "where day >= ?" range scans for weekly roll-ups.
