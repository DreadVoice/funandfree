package com.funandfree.events.event;

import com.funandfree.events.common.NotFoundException;
import com.funandfree.events.event.dto.CreateEventRequest;
import com.funandfree.events.event.dto.EventResponse;
import com.funandfree.events.event.dto.UpdateEventRequest;
import com.funandfree.events.security.CurrentUser;
import com.funandfree.events.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository events;
    private final EventService eventService;

    EventController(EventRepository events, EventService eventService) {
        this.events = events;
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> upcoming(@RequestParam(defaultValue = "20") int limit) {
        return events.findUpcoming(limit).stream().map(EventResponse::from).toList();
    }

    @GetMapping("/search")
    public List<EventResponse> search(@RequestParam String q,
                                      @RequestParam(defaultValue = "20") int limit) {
        return events.search(q, limit).stream().map(EventResponse::from).toList();
    }

    @GetMapping("/mine")
    public List<EventResponse> mine(@CurrentUser AppUser organizer) {
        return eventService.forOrganizer(organizer).stream().map(EventResponse::from).toList();
    }

    @GetMapping("/{eventId}")
    public EventResponse byId(@PathVariable long eventId) {
        return events.findById(eventId)
                .map(EventResponse::from)
                .orElseThrow(() -> new NotFoundException("event " + eventId + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest request,
                                @CurrentUser AppUser organizer) {
        return EventResponse.from(eventService.create(organizer, request));
    }

    @PutMapping("/{eventId}")
    public EventResponse update(@PathVariable long eventId,
                                @Valid @RequestBody UpdateEventRequest request,
                                @CurrentUser AppUser organizer) {
        return EventResponse.from(eventService.update(organizer, eventId, request));
    }
}
