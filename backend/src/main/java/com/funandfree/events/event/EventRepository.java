package com.funandfree.events.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    private static final String COLUMNS = """
            id, organizer_id, title, description, venue, starts_at_utc,
            time_zone, capacity, seats_taken, status
            """;

    private final JdbcClient db;

    EventRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Event> findUpcoming(int limit) {
        return db.sql("select " + COLUMNS + """
                  from event
                 where status = 'PUBLISHED'
                   and starts_at_utc >= :now
                 order by starts_at_utc
                 limit :limit
                """)
                .param("now", Instant.now().toString())
                .param("limit", limit)
                .query(EventRepository::mapEvent)
                .list();
    }

    public List<Event> search(String query, int limit) {
        return db.sql("""
                select e.id, e.organizer_id, e.title, e.description, e.venue,
                       e.starts_at_utc, e.time_zone, e.capacity, e.seats_taken, e.status
                  from event_search s
                  join event e on e.id = s.rowid
                 where event_search match :query
                   and e.status = 'PUBLISHED'
                 order by rank
                 limit :limit
                """)
                .param("query", query)
                .param("limit", limit)
                .query(EventRepository::mapEvent)
                .list();
    }

    public boolean tryClaimSeat(long eventId) {
        return db.sql("""
                update event
                   set seats_taken = seats_taken + 1,
                       updated_at  = strftime('%Y-%m-%dT%H:%M:%SZ')
                 where id = :id
                   and status = 'PUBLISHED'
                   and seats_taken < capacity
                """)
                .param("id", eventId)
                .update() == 1;
    }

    public long insert(long organizerId, String title, String description, String venue,
                       Instant startsAtUtc, String timeZone, int capacity) {
        return db.sql("""
                insert into event (organizer_id, title, description, venue,
                                   starts_at_utc, time_zone, capacity)
                values (:organizerId, :title, :description, :venue,
                        :startsAt, :timeZone, :capacity)
                returning id
                """)
                .param("organizerId", organizerId)
                .param("title", title)
                .param("description", description)
                .param("venue", venue)
                .param("startsAt", startsAtUtc.toString())
                .param("timeZone", timeZone)
                .param("capacity", capacity)
                .query(Long.class)
                .single();
    }

    private static Event mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new Event(
                rs.getLong("id"),
                rs.getLong("organizer_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("venue"),
                Instant.parse(rs.getString("starts_at_utc")),
                rs.getString("time_zone"),
                rs.getInt("capacity"),
                rs.getInt("seats_taken"),
                rs.getString("status"));
    }
}
