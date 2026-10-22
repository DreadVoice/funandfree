import type {
  ApiErrorBody,
  BookingOutcomeResponse,
  BookingResponse,
  CancellationResponse,
  EventInput,
  EventResponse,
} from "../types/api";

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

type TokenProvider = () => Promise<string | null>;

let tokenProvider: TokenProvider = async () => null;

export function setTokenProvider(provider: TokenProvider): void {
  tokenProvider = provider;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  authenticated?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (options.authenticated) {
    const token = await tokenProvider();
    if (!token) {
      throw new ApiError(401, "You need to sign in first.");
    }
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, `Could not reach the API at ${baseUrl}.`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const parsed: unknown = text ? safeParse(text) : null;

  if (!response.ok) {
    throw new ApiError(response.status, messageFrom(parsed, response.status));
  }

  return parsed as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function messageFrom(parsed: unknown, status: number): string {
  if (parsed && typeof parsed === "object" && "message" in parsed) {
    const message = (parsed as ApiErrorBody).message;
    if (message) {
      return message;
    }
  }
  if (status === 401) {
    return "Your session has expired. Sign in again.";
  }
  if (status === 403) {
    return "You do not have permission to do that.";
  }
  return `Request failed with status ${status}.`;
}

export const api = {
  listEvents: (limit = 20) => request<EventResponse[]>(`/api/events?limit=${limit}`),

  searchEvents: (query: string, limit = 20) =>
    request<EventResponse[]>(`/api/events/search?q=${encodeURIComponent(query)}&limit=${limit}`),

  getEvent: (eventId: number) => request<EventResponse>(`/api/events/${eventId}`),

  myEvents: () => request<EventResponse[]>("/api/events/mine", { authenticated: true }),

  createEvent: (input: EventInput) =>
    request<EventResponse>("/api/events", { method: "POST", body: input, authenticated: true }),

  updateEvent: (eventId: number, input: EventInput) =>
    request<EventResponse>(`/api/events/${eventId}`, {
      method: "PUT",
      body: input,
      authenticated: true,
    }),

  book: (eventId: number) =>
    request<BookingOutcomeResponse>(`/api/events/${eventId}/bookings`, {
      method: "POST",
      authenticated: true,
    }),

  cancelBooking: (eventId: number) =>
    request<CancellationResponse>(`/api/events/${eventId}/bookings/me`, {
      method: "DELETE",
      authenticated: true,
    }),

  leaveWaitlist: (eventId: number) =>
    request<void>(`/api/events/${eventId}/waitlist/me`, {
      method: "DELETE",
      authenticated: true,
    }),

  myBookings: () => request<BookingResponse[]>("/api/bookings/me", { authenticated: true }),
};
