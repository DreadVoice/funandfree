create table reminder_log (
    id       integer primary key autoincrement,
    event_id integer not null references event (id) on delete cascade,
    user_id  integer not null references app_user (id),
    channel  text    not null default 'EMAIL'
                     check (channel in ('EMAIL')),
    sent_at  text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ'))
);

create unique index ux_reminder_once on reminder_log (event_id, user_id, channel);
create index ix_reminder_event on reminder_log (event_id);
