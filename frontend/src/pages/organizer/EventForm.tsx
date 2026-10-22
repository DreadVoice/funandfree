import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button } from "../../components/ui/Button";
import { Field, TextArea, TextInput } from "../../components/ui/Field";
import { Alert, Skeletons } from "../../components/ui/Feedback";
import { api, ApiError } from "../../lib/api";
import { browserTimeZone, fromLocalInputValue, toLocalInputValue } from "../../lib/time";
import type { EventInput } from "../../types/api";

interface FormState {
  title: string;
  description: string;
  venue: string;
  startsAtLocal: string;
  endsAtLocal: string;
  timeZone: string;
  capacity: string;
  imageUrl: string;
}

const empty: FormState = {
  title: "",
  description: "",
  venue: "",
  startsAtLocal: "",
  endsAtLocal: "",
  timeZone: browserTimeZone,
  capacity: "20",
  imageUrl: "",
};

export function EventForm() {
  const { eventId } = useParams();
  const editing = eventId !== undefined;
  const id = Number(eventId);
  const navigate = useNavigate();

  const [form, setForm] = useState<FormState>(empty);
  const [loading, setLoading] = useState(editing);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!editing) {
      return;
    }
    let active = true;
    void (async () => {
      try {
        const loaded = await api.getEvent(id);
        if (!active) {
          return;
        }
        setForm({
          title: loaded.title,
          description: loaded.description ?? "",
          venue: loaded.venue ?? "",
          startsAtLocal: toLocalInputValue(loaded.startsAtUtc),
          endsAtLocal: "",
          timeZone: loaded.timeZone,
          capacity: String(loaded.capacity),
          imageUrl: "",
        });
      } catch (caught) {
        setError(caught instanceof ApiError ? caught.message : "Could not load that event.");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [editing, id]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function toInput(): EventInput {
    return {
      title: form.title.trim(),
      description: form.description.trim() || null,
      venue: form.venue.trim() || null,
      startsAtUtc: fromLocalInputValue(form.startsAtLocal),
      endsAtUtc: form.endsAtLocal ? fromLocalInputValue(form.endsAtLocal) : null,
      timeZone: form.timeZone.trim(),
      latitude: null,
      longitude: null,
      capacity: Number(form.capacity),
      imageUrl: form.imageUrl.trim() || null,
    };
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const saved = editing ? await api.updateEvent(id, toInput()) : await api.createEvent(toInput());
      navigate(`/organizer`, { replace: true, state: { savedId: saved.id } });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Could not save the event.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <Skeletons count={2} />;
  }

  return (
    <div className="stack" style={{ gap: 24, maxWidth: 760 }}>
      <h1>{editing ? "Edit event" : "New event"}</h1>

      <form
        className="card stack"
        onSubmit={(formEvent) => {
          formEvent.preventDefault();
          void submit();
        }}
      >
        {error ? <Alert tone="danger">{error}</Alert> : null}

        <Field label="Title">
          <TextInput
            value={form.title}
            required
            maxLength={200}
            onChange={(inputEvent) => update("title", inputEvent.target.value)}
          />
        </Field>

        <Field label="Description">
          <TextArea
            value={form.description}
            maxLength={5000}
            onChange={(inputEvent) => update("description", inputEvent.target.value)}
          />
        </Field>

        <div className="form-grid">
          <Field label="Venue">
            <TextInput
              value={form.venue}
              maxLength={200}
              onChange={(inputEvent) => update("venue", inputEvent.target.value)}
            />
          </Field>

          <Field label="Capacity">
            <TextInput
              type="number"
              min={1}
              max={100000}
              value={form.capacity}
              required
              onChange={(inputEvent) => update("capacity", inputEvent.target.value)}
            />
          </Field>
        </div>

        <div className="form-grid">
          <Field label="Starts" hint="Entered in your own clock time.">
            <TextInput
              type="datetime-local"
              value={form.startsAtLocal}
              required
              onChange={(inputEvent) => update("startsAtLocal", inputEvent.target.value)}
            />
          </Field>

          <Field label="Ends" hint="Optional.">
            <TextInput
              type="datetime-local"
              value={form.endsAtLocal}
              onChange={(inputEvent) => update("endsAtLocal", inputEvent.target.value)}
            />
          </Field>
        </div>

        <Field label="Event time zone" hint="IANA name, for example America/Chicago.">
          <TextInput
            value={form.timeZone}
            required
            maxLength={64}
            onChange={(inputEvent) => update("timeZone", inputEvent.target.value)}
          />
        </Field>

        <Field label="Image URL" hint="Posters are never served from the API.">
          <TextInput
            type="url"
            value={form.imageUrl}
            maxLength={2000}
            onChange={(inputEvent) => update("imageUrl", inputEvent.target.value)}
          />
        </Field>

        <div className="row">
          <Button type="submit" disabled={busy}>
            {busy ? "Saving..." : editing ? "Save changes" : "Create event"}
          </Button>
          <Button variant="text" onClick={() => navigate("/organizer")}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
