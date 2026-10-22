package com.funandfree.events.booking.dto;

public record CancellationResponse(boolean seatTransferred, Long promotedUserId) {
}
