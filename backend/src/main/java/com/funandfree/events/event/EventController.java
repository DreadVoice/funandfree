package com.funandfree.events.event;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventRepository events;

    EventController(EventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<Event> upcoming(@RequestParam(defaultValue = "20") int limit) {
        return events.findUpcoming(limit);
    }

    @GetMapping("/search")
    public List<Event> search(@RequestParam String q,
                              @RequestParam(defaultValue = "20") int limit) {
        return events.search(q, limit);
    }
}
