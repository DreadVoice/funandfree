create table outbox_message (
    id              integer primary key autoincrement,
    topic           text    not null,
    payload         text    not null,
    dedup_key       text,
    status          text    not null default 'PENDING'
                            check (status in ('PENDING', 'SENDING', 'SENT', 'DEAD')),
    attempt_count   integer not null default 0
                            check (attempt_count >= 0),
    next_attempt_at text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ')),
    last_error      text,
    created_at      text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ')),
    completed_at    text
);

create unique index ux_outbox_dedup on outbox_message (dedup_key)
    where dedup_key is not null;

create index ix_outbox_due on outbox_message (next_attempt_at, id)
    where status = 'PENDING';

create index ix_outbox_dead on outbox_message (id)
    where status = 'DEAD';
