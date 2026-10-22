import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, Badge, Empty, Skeletons } from "../components/ui/Feedback";
import { api, ApiError } from "../lib/api";
import { formatInZone } from "../lib/time";
import type { BookingResponse, EventResponse } from "../types/api";

interface Row {
  booking: BookingResponse;
  event: EventResponse | null;
}

export function MyBookings() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const bookings = await api.myBookings();
      const events = await Promise.all(
        bookings.map((booking) => api.getEvent(booking.eventId).catch(() => null)),
      );
      setRows(bookings.map((booking, index) => ({ booking, event: events[index] ?? null })));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Could not load your bookings.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <Skeletons count={3} />;
  }

  return (
    <div className="stack" style={{ gap: 24 }}>
      <h1>My bookings</h1>
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {rows.length === 0 ? (
        <Empty title="No bookings yet">
          <Link to="/">Find an event</Link> and book a seat.
        </Empty>
      ) : (
        <div className="stack">
          {rows.map(({ booking, event }) => (
            <div className="card spread" key={booking.id}>
              <div className="stack" style={{ gap: 4 }}>
                <h3>{event ? event.title : `Event ${booking.eventId}`}</h3>
                {event ? (
                  <span className="small muted">
                    {formatInZone(event.startsAtUtc, event.timeZone)}
                  </span>
                ) : null}
                <span className="small muted">
                  Booked {formatInZone(booking.createdAt, "UTC")} UTC
                </span>
              </div>
              <div className="row">
                <Badge tone={booking.status === "CONFIRMED" ? "success" : "neutral"}>
                  {booking.status}
                </Badge>
                {event ? <Link to={`/events/${event.id}`}>Open</Link> : null}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
