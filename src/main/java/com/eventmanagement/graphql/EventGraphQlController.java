package com.eventmanagement.graphql;

import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.service.EventService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class EventGraphQlController {

    private final EventService eventService;

    public EventGraphQlController(EventService eventService) {
        this.eventService = eventService;
    }

    @QueryMapping
    public List<EventResponse> events() {
        return eventService.getAllEvents();
    }

    @QueryMapping
    public EventResponse event(@Argument Long id) {
        return eventService.getEventById(id);
    }

    @SchemaMapping(typeName = "Event", field = "startDate")
    public String startDate(EventResponse event) {
        return event.getStartDate().toString();
    }

    @SchemaMapping(typeName = "Event", field = "endDate")
    public String endDate(EventResponse event) {
        return event.getEndDate().toString();
    }
}
