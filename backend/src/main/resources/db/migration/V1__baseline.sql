create table app_user (
    id            integer primary key autoincrement,
    firebase_uid  text    not null unique,
    email         text,
    display_name  text,
    role          text    not null default 'ATTENDEE'
                          check (role in ('ATTENDEE', 'ORGANIZER')),
    created_at    text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ'))
);

create table event (
    id            integer primary key autoincrement,
    organizer_id  integer not null references app_user (id),
    title         text    not null,
    description   text,
    venue         text,
    starts_at_utc text    not null,
    ends_at_utc   text,
    time_zone     text    not null,
    latitude      real,
    longitude     real,
    capacity      integer not null check (capacity > 0),
    seats_taken   integer not null default 0
                          check (seats_taken >= 0 and seats_taken <= capacity),
    status        text    not null default 'PUBLISHED'
                          check (status in ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    image_url     text,
    created_at    text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ')),
    updated_at    text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ'))
);

create index ix_event_discovery on event (starts_at_utc) where status = 'PUBLISHED';
create index ix_event_location on event (latitude, longitude);
create index ix_event_organizer on event (organizer_id);

create table booking (
    id           integer primary key autoincrement,
    event_id     integer not null references event (id) on delete cascade,
    user_id      integer not null references app_user (id),
    status       text    not null default 'CONFIRMED'
                         check (status in ('CONFIRMED', 'CANCELLED')),
    created_at   text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ')),
    cancelled_at text
);

create unique index ux_booking_active on booking (event_id, user_id) where status = 'CONFIRMED';
create index ix_booking_user on booking (user_id);

create table waitlist_entry (
    id         integer primary key autoincrement,
    event_id   integer not null references event (id) on delete cascade,
    user_id    integer not null references app_user (id),
    status     text    not null default 'WAITING'
                       check (status in ('WAITING', 'PROMOTED', 'CANCELLED')),
    created_at text    not null default (strftime('%Y-%m-%dT%H:%M:%SZ')),
    promoted_at text
);

create unique index ux_waitlist_active on waitlist_entry (event_id, user_id) where status = 'WAITING';
create index ix_waitlist_queue on waitlist_entry (event_id, id) where status = 'WAITING';
