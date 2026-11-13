package com.funandfree.events.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcClient db;

    OutboxRepository(JdbcClient db) {
        this.db = db;
    }

    public boolean enqueue(String topic, String payload, String dedupKey) {
        return db.sql("""
                insert into outbox_message (topic, payload, dedup_key)
                values (:topic, :payload, :dedupKey)
                on conflict (dedup_key) where dedup_key is not null do nothing
                """)
                .param("topic", topic)
                .param("payload", payload)
                .param("dedupKey", dedupKey)
                .update() == 1;
    }

    public Optional<OutboxMessage> claimNext(Instant now, Instant visibleAgainAt) {
        return db.sql("""
                update outbox_message
                   set status = 'SENDING',
                       attempt_count = attempt_count + 1,
                       next_attempt_at = :visibleAgainAt
                 where id = (select id
                               from outbox_message
                              where status = 'PENDING'
                                and next_attempt_at <= :now
                              order by next_attempt_at, id
                              limit 1)
                returning id, topic, payload, attempt_count
                """)
                .param("now", iso(now))
                .param("visibleAgainAt", iso(visibleAgainAt))
                .query(OutboxRepository::mapMessage)
                .optional();
    }

    public void markSent(long id, Instant now) {
        db.sql("""
                update outbox_message
                   set status = 'SENT', completed_at = :now, last_error = null
                 where id = :id
                """)
                .param("id", id)
                .param("now", iso(now))
                .update();
    }

    public void reschedule(long id, Instant nextAttemptAt, String error) {
        db.sql("""
                update outbox_message
                   set status = 'PENDING', next_attempt_at = :next, last_error = :error
                 where id = :id
                """)
                .param("id", id)
                .param("next", iso(nextAttemptAt))
                .param("error", error)
                .update();
    }

    public void markDead(long id, Instant now, String error) {
        db.sql("""
                update outbox_message
                   set status = 'DEAD', completed_at = :now, last_error = :error
                 where id = :id
                """)
                .param("id", id)
                .param("now", iso(now))
                .param("error", error)
                .update();
    }

    public int releaseStale(Instant now) {
        return db.sql("""
                update outbox_message
                   set status = 'PENDING'
                 where status = 'SENDING'
                   and next_attempt_at <= :now
                """)
                .param("now", iso(now))
                .update();
    }

    public OutboxStats stats() {
        return db.sql("""
                select sum(status = 'PENDING') as pending,
                       sum(status = 'SENT')    as sent,
                       sum(status = 'DEAD')    as dead
                  from outbox_message
                """)
                .query((ResultSet rs, int rowNum) -> new OutboxStats(
                        rs.getInt("pending"), rs.getInt("sent"), rs.getInt("dead")))
                .single();
    }

    public Optional<String> lastErrorFor(String dedupKey) {
        return db.sql("select last_error from outbox_message where dedup_key = :dedupKey")
                .param("dedupKey", dedupKey)
                .query(String.class)
                .optional();
    }

    public Optional<String> statusOf(String dedupKey) {
        return db.sql("select status from outbox_message where dedup_key = :dedupKey")
                .param("dedupKey", dedupKey)
                .query(String.class)
                .optional();
    }

    private static String iso(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static OutboxMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxMessage(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("payload"),
                rs.getInt("attempt_count"));
    }
}
