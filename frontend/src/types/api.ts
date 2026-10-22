export type EventStatus = "DRAFT" | "PUBLISHED" | "CANCELLED";

export type BookingStatus = "CONFIRMED" | "CANCELLED";

export type BookingOutcome =
  | "CONFIRMED"
  | "WAITLISTED"
  | "ALREADY_BOOKED"
  | "ALREADY_WAITLISTED";

export interface EventResponse {
  id: number;
  organizerId: number;
  title: string;
  description: string | null;
  venue: string | null;
  startsAtUtc: string;
  timeZone: string;
  capacity: number;
  seatsTaken: number;
  seatsRemaining: number;
  status: EventStatus;
}

export interface BookingResponse {
  id: number;
  eventId: number;
  status: BookingStatus;
  createdAt: string;
  cancelledAt: string | null;
}

export interface BookingOutcomeResponse {
  outcome: BookingOutcome;
}

export interface CancellationResponse {
  seatTransferred: boolean;
  promotedUserId: number | null;
}

export interface EventInput {
  title: string;
  description: string | null;
  venue: string | null;
  startsAtUtc: string;
  endsAtUtc: string | null;
  timeZone: string;
  latitude: number | null;
  longitude: number | null;
  capacity: number;
  imageUrl: string | null;
}

export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  timestamp: string;
}
