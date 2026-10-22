import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Button } from "../components/ui/Button";
import { Alert, Badge, SeatMeter, Skeletons } from "../components/ui/Feedback";
import { api, ApiError } from "../lib/api";
import { useAuth } from "../lib/auth";
import { formatInZone, relativeToNow, zoneAbbreviation } from "../lib/time";
import type { BookingResponse, EventResponse } from "../types/api";

export function EventDetail() {
  const { eventId } = useParams();
  const id = Number(eventId);
  const navigate = useNavigate();
  const { user } = useAuth();

  const [event, setEvent] = useState<EventResponse | null>(null);
  const [myBookings, setMyBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const loaded = await api.getEvent(id);
      setEvent(loaded);
      if (user) {
        setMyBookings(await api.myBookings());
      } else {
        setMyBookings([]);
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Could not load this event.");
    } finally {
      setLoading(false);
    }
  }, [id, user]);

  useEffect(() => {
    if (Number.isNaN(id)) {
      setError("That event id is not valid.");
      setLoading(false);
      return;
    }
    void load();
  }, [id, load]);

  const confirmed = myBookings.some(
    (booking) => booking.eventId === id && booking.status === "CONFIRMED",
  );

  async function act(action: () => Promise<string>) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      setNotice(await action());
      await load();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "That did not work.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <Skeletons count={2} />;
  }

  if (!event) {
    return (
      <div className="stack">
        <Alert tone="danger">{error ?? "Event not found."}</Alert>
        <Link to="/">Back to all events</Link>
      </div>
    );
  }

  const full = event.seatsRemaining === 0;

  return (
    <div className="stack" style={{ gap: 24, maxWidth: 760 }}>
      <Link to="/" className="small">
        Back to all events
      </Link>

      <div className="stack" style={{ gap: 8 }}>
        <div className="row">
          <Badge tone={event.status === "PUBLISHED" ? "primary" : "danger"}>{event.status}</Badge>
          {full ? <Badge tone="danger">Waitlist only</Badge> : null}
          {confirmed ? <Badge tone="success">You are going</Badge> : null}
        </div>
        <h1>{event.title}</h1>
        <p className="muted">
          {formatInZone(event.startsAtUtc, event.timeZone)}{" "}
          ({zoneAbbreviation(event.startsAtUtc, event.timeZone)}) · {relativeToNow(event.startsAtUtc)}
        </p>
        {event.venue ? <p className="muted">{event.venue}</p> : null}
      </div>

      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {error ? <Alert tone="danger">{error}</Alert> : null}

      <div className="card stack">
        <SeatMeter taken={event.seatsTaken} capacity={event.capacity} />
        {!user ? (
          <div className="stack">
            <p className="small muted">Sign in to book a seat or join the waitlist.</p>
            <Button onClick={() => navigate("/login", { state: { from: `/events/${id}` } })}>
              Sign in to book
            </Button>
          </div>
        ) : confirmed ? (
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              act(async () => {
                const result = await api.cancelBooking(event.id);
                return result.seatTransferred
                  ? "Booking cancelled. Your seat went to the next person on the waitlist."
                  : "Booking cancelled and the seat is free again.";
              })
            }
          >
            Cancel my booking
          </Button>
        ) : (
          <Button
            disabled={busy || event.status !== "PUBLISHED"}
            onClick={() =>
              act(async () => {
                const result = await api.book(event.id);
                return result.outcome === "CONFIRMED"
                  ? "Your seat is confirmed."
                  : "The event is full, so you are on the waitlist.";
              })
            }
          >
            {full ? "Join the waitlist" : "Book a seat"}
          </Button>
        )}
      </div>

      {event.description ? (
        <div className="stack" style={{ gap: 8 }}>
          <h2>About</h2>
          <p style={{ whiteSpace: "pre-wrap" }}>{event.description}</p>
        </div>
      ) : null}
    </div>
  );
}
