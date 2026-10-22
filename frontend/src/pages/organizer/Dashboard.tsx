import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "../../components/ui/Button";
import { Alert, Badge, Empty, SeatMeter, Skeletons } from "../../components/ui/Feedback";
import { api, ApiError } from "../../lib/api";
import { formatInZone } from "../../lib/time";
import type { EventResponse } from "../../types/api";

export function Dashboard() {
  const navigate = useNavigate();
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEvents(await api.myEvents());
    } catch (caught) {
      const message =
        caught instanceof ApiError && caught.status === 403
          ? "Your account is an attendee. An organizer role has to be set on your user row in the database."
          : caught instanceof ApiError
            ? caught.message
            : "Could not load your events.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="stack" style={{ gap: 24 }}>
      <div className="spread">
        <h1>Your events</h1>
        <Button onClick={() => navigate("/organizer/events/new")}>New event</Button>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}

      {loading ? (
        <Skeletons count={3} />
      ) : events.length === 0 && !error ? (
        <Empty title="You have not created an event yet">
          Publish one and it appears on the public discovery page straight away.
        </Empty>
      ) : (
        <div className="grid">
          {events.map((event) => (
            <div className="card stack" key={event.id}>
              <div className="spread">
                <Badge tone={event.status === "PUBLISHED" ? "primary" : "danger"}>
                  {event.status}
                </Badge>
                <Link to={`/organizer/events/${event.id}`} className="small">
                  Edit
                </Link>
              </div>
              <h3>{event.title}</h3>
              <span className="small muted">{formatInZone(event.startsAtUtc, event.timeZone)}</span>
              <SeatMeter taken={event.seatsTaken} capacity={event.capacity} />
              <Link to={`/events/${event.id}`} className="small">
                View public page
              </Link>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
