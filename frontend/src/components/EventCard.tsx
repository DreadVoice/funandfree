import { Link } from "react-router-dom";
import { formatInZone, relativeToNow, zoneAbbreviation } from "../lib/time";
import type { EventResponse } from "../types/api";
import { Badge, SeatMeter } from "./ui/Feedback";

export function EventCard({ event }: { event: EventResponse }) {
  const full = event.seatsRemaining === 0;

  return (
    <Link to={`/events/${event.id}`} className="card card-interactive stack" style={{ color: "inherit" }}>
      <div className="spread">
        <Badge tone={full ? "danger" : "success"}>
          {full ? "Waitlist only" : `${event.seatsRemaining} left`}
        </Badge>
        <span className="small muted">{relativeToNow(event.startsAtUtc)}</span>
      </div>
      <h3>{event.title}</h3>
      <span className="small muted">
        {formatInZone(event.startsAtUtc, event.timeZone)} ({zoneAbbreviation(event.startsAtUtc, event.timeZone)})
      </span>
      {event.venue ? <span className="small muted">{event.venue}</span> : null}
      <SeatMeter taken={event.seatsTaken} capacity={event.capacity} />
    </Link>
  );
}
