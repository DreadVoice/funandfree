package com.funandfree.events.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {

    private static final String CHANNEL = "EMAIL";

    private final JdbcClient db;

    ReminderRepository(JdbcClient db) {
        this.db = db;
    }

    public List<DueReminder> findDue(Instant from, Instant until) {
        return db.sql("""
                select e.id as event_id, e.title, e.starts_at_utc, e.time_zone, e.venue,
                       u.id as user_id, u.email, u.display_name
                  from event e
                  join booking b on b.event_id = e.id and b.status = 'CONFIRMED'
                  join app_user u on u.id = b.user_id
                 where e.status = 'PUBLISHED'
                   and e.starts_at_utc >= :from
                   and e.starts_at_utc <= :until
                   and not exists (select 1
                                     from reminder_log r
                                    where r.event_id = e.id
                                      and r.user_id = u.id
                                      and r.channel = :channel)
                 order by e.starts_at_utc, u.id
                """)
                .param("from", from.toString())
                .param("until", until.toString())
                .param("channel", CHANNEL)
                .query(ReminderRepository::mapDue)
                .list();
    }

    public boolean claim(long eventId, long userId) {
        return db.sql("""
                insert into reminder_log (event_id, user_id, channel)
                values (:eventId, :userId, :channel)
                on conflict (event_id, user_id, channel) do nothing
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .param("channel", CHANNEL)
                .update() == 1;
    }

    public void release(long eventId, long userId) {
        db.sql("""
                delete from reminder_log
                 where event_id = :eventId and user_id = :userId and channel = :channel
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .param("channel", CHANNEL)
                .update();
    }

    public int countSent(long eventId) {
        return db.sql("select count(*) from reminder_log where event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();
    }

    private static DueReminder mapDue(ResultSet rs, int rowNum) throws SQLException {
        return new DueReminder(
                rs.getLong("event_id"),
                rs.getString("title"),
                Instant.parse(rs.getString("starts_at_utc")),
                rs.getString("time_zone"),
                rs.getString("venue"),
                rs.getLong("user_id"),
                rs.getString("email"),
                rs.getString("display_name"));
    }
}
