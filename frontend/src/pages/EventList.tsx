import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { EventCard } from "../components/EventCard";
import { SearchFilters } from "../components/SearchFilters";
import { Alert, Empty, Skeletons } from "../components/ui/Feedback";
import { api, ApiError } from "../lib/api";
import type { EventResponse } from "../types/api";

export function EventList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [availableOnly, setAvailableOnly] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = query ? await api.searchEvents(query) : await api.listEvents();
      setEvents(result);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Could not load events.");
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    void load();
  }, [load]);

  const visible = availableOnly ? events.filter((event) => event.seatsRemaining > 0) : events;

  return (
    <div className="stack" style={{ gap: 24 }}>
      <div className="stack" style={{ gap: 4 }}>
        <h1>Discover events</h1>
        <p className="muted">Free events and RSVPs, with a waitlist that moves when people cancel.</p>
      </div>

      <SearchFilters
        initialQuery={query}
        availableOnly={availableOnly}
        onSearch={(next) => setSearchParams(next ? { q: next } : {})}
        onAvailableOnlyChange={setAvailableOnly}
      />

      {error ? <Alert tone="danger">{error}</Alert> : null}

      {loading ? (
        <Skeletons />
      ) : visible.length === 0 ? (
        <Empty title={query ? `Nothing matches "${query}"` : "No upcoming events yet"}>
          {query ? "Try a different word, or clear the search." : "Once an organizer publishes one, it shows up here."}
        </Empty>
      ) : (
        <div className="grid">
          {visible.map((event) => (
            <EventCard event={event} key={event.id} />
          ))}
        </div>
      )}
    </div>
  );
}
