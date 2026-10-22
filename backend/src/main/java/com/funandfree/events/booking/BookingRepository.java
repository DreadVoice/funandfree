package com.funandfree.events.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private static final String COLUMNS =
            "id, event_id, user_id, status, created_at, cancelled_at";

    private final JdbcClient db;

    BookingRepository(JdbcClient db) {
        this.db = db;
    }

    public boolean hasActiveBooking(long eventId, long userId) {
        return db.sql("""
                select count(*) from booking
                 where event_id = :eventId and user_id = :userId and status = 'CONFIRMED'
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .query(Integer.class)
                .single() > 0;
    }

    public long insertConfirmed(long eventId, long userId) {
        return db.sql("""
                insert into booking (event_id, user_id, status)
                values (:eventId, :userId, 'CONFIRMED')
                returning id
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    public int cancelActive(long eventId, long userId) {
        return db.sql("""
                update booking
                   set status = 'CANCELLED',
                       cancelled_at = strftime('%Y-%m-%dT%H:%M:%SZ')
                 where event_id = :eventId
                   and user_id = :userId
                   and status = 'CONFIRMED'
                """)
                .param("eventId", eventId)
                .param("userId", userId)
                .update();
    }

    public int countConfirmed(long eventId) {
        return db.sql("select count(*) from booking where event_id = :eventId and status = 'CONFIRMED'")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();
    }

    public List<Booking> findByUser(long userId) {
        return db.sql("select " + COLUMNS + """
                  from booking
                 where user_id = :userId
                 order by id desc
                """)
                .param("userId", userId)
                .query(BookingRepository::mapBooking)
                .list();
    }

    public List<Booking> findConfirmedByEvent(long eventId) {
        return db.sql("select " + COLUMNS + """
                  from booking
                 where event_id = :eventId and status = 'CONFIRMED'
                 order by id
                """)
                .param("eventId", eventId)
                .query(BookingRepository::mapBooking)
                .list();
    }

    private static Booking mapBooking(ResultSet rs, int rowNum) throws SQLException {
        String cancelledAt = rs.getString("cancelled_at");
        return new Booking(
                rs.getLong("id"),
                rs.getLong("event_id"),
                rs.getLong("user_id"),
                BookingStatus.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("created_at")),
                cancelledAt == null ? null : Instant.parse(cancelledAt));
    }
}
