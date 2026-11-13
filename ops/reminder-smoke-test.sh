#!/bin/sh
set -eu

DB="${APP_DB_PATH:-/var/lib/event-platform/data/events.db}"
APP_USER="${APP_USER:-appsvc}"
LEAD_HOURS="${LEAD_HOURS:-8}"
ACTION="${1:-}"

run_sql() {
  sudo -u "${APP_USER}" sqlite3 "${DB}" "$1"
}

case "${ACTION}" in
seed)
  STARTS_AT="$(date -u -d "+${LEAD_HOURS} hours" '+%Y-%m-%dT%H:%M:%SZ')"
  run_sql "
begin immediate;
insert into reminder_log (event_id, user_id, channel)
  select e.id, b.user_id, 'EMAIL'
    from event e
    join booking b on b.event_id = e.id and b.status = 'CONFIRMED'
   where e.title <> 'Reminder smoke test'
     and e.status = 'PUBLISHED'
 on conflict (event_id, user_id, channel) do nothing;
insert into app_user (firebase_uid, email, display_name, role) values
  ('smoke-delivered', 'delivered@resend.dev', 'Delivered Sink', 'ATTENDEE'),
  ('smoke-bounced',   'bounced@resend.dev',   'Bounced Sink',   'ATTENDEE'),
  ('smoke-rejected',  'rejected@example.com', 'Rejected Sink',  'ATTENDEE')
 on conflict (firebase_uid) do nothing;
insert into event (organizer_id, title, description, venue, starts_at_utc, time_zone, capacity, seats_taken, status)
  select min(id), 'Reminder smoke test', 'Seeded to exercise the live Resend path', 'Nowhere',
         '${STARTS_AT}', 'Etc/UTC', 10, 3, 'PUBLISHED'
    from app_user;
insert into booking (event_id, user_id, status)
  select (select max(id) from event), id, 'CONFIRMED'
    from app_user
   where firebase_uid in ('smoke-delivered', 'smoke-bounced', 'smoke-rejected');
commit;
"
  echo "seeded event starting ${STARTS_AT}"
  echo "reminders for every other published event are held back"
  ;;
check)
  run_sql "
select 'event', id, title, starts_at_utc from event where title = 'Reminder smoke test';
select 'claimed', r.event_id, u.email from reminder_log r join app_user u on u.id = r.user_id;
"
  ;;
clean)
  run_sql "
begin immediate;
delete from reminder_log where event_id in (select id from event where title = 'Reminder smoke test');
delete from booking where event_id in (select id from event where title = 'Reminder smoke test');
delete from event where title = 'Reminder smoke test';
delete from app_user where firebase_uid in ('smoke-delivered', 'smoke-bounced', 'smoke-rejected');
commit;
"
  echo "smoke test rows removed"
  echo "held-back reminder_log rows for real events are left in place"
  ;;
release)
  run_sql "delete from reminder_log where user_id not in (select id from app_user where firebase_uid like 'smoke-%');"
  echo "held-back reminders released, they will send on the next run"
  ;;
*)
  echo "usage: $0 seed|check|clean|release" >&2
  exit 1
  ;;
esac
