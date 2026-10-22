package com.funandfree.events.waitlist;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WaitlistRepository {

    private final JdbcClient db;

    WaitlistRepository(JdbcClient db) {
        this.db = db;
    }

    public boolean isWaiting(long eventId, long userId) {
        return db.sql("""
                select count(*) from waitlist_entry
                 where event_id = :eventId and user_id = :userId and status = 'WAITING'
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .query(Integer.class)
                .single() > 0;
    }

    public long insertWaiting(long eventId, long userId) {
        return db.sql("""
                insert into waitlist_entry (event_id, user_id, status)
                values (:eventId, :userId, 'WAITING')
                returning id
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    public Optional<Long> promoteNext(long eventId) {
        return db.sql("""
                update waitlist_entry
                   set status = 'PROMOTED',
                       promoted_at = strftime('%Y-%m-%dT%H:%M:%SZ')
                 where id = (select id from waitlist_entry
                              where event_id = :eventId and status = 'WAITING'
                              order by id
                              limit 1)
                returning user_id
                """)
                .param("eventId", eventId)
                .query(Long.class)
                .optional();
    }

    public int cancelWaiting(long eventId, long userId) {
        return db.sql("""
                update waitlist_entry
                   set status = 'CANCELLED'
                 where event_id = :eventId and user_id = :userId and status = 'WAITING'
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .update();
    }

    public int countWaiting(long eventId) {
        return db.sql("select count(*) from waitlist_entry where event_id = :eventId and status = 'WAITING'")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();
    }
}
