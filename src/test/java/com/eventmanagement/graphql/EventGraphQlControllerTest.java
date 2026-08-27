package com.eventmanagement.graphql;

import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.dto.response.TicketTypeResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;

@GraphQlTest(EventGraphQlController.class)
class EventGraphQlControllerTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockBean
    private EventService eventService;

    @Test
    void events_returnsPublishedEventsWithTicketTypes() {
        EventResponse response = EventResponse.builder()
                .id(1L)
                .organizerId(9L)
                .title("Music Festival")
                .location("City Park")
                .startDate(LocalDateTime.of(2026, 6, 1, 18, 0))
                .endDate(LocalDateTime.of(2026, 6, 1, 23, 0))
                .status(Event.EventStatus.PUBLISHED)
                .ticketTypes(List.of(
                        TicketTypeResponse.builder()
                                .id(5L)
                                .name("General")
                                .price(new BigDecimal("50.00"))
                                .quantity(100)
                                .available(80)
                                .build()
                ))
                .build();
        when(eventService.getAllEvents()).thenReturn(List.of(response));

        graphQlTester.document("{ events { id title status ticketTypes { name available } } }")
                .execute()
                .path("events[0].id").entity(String.class).isEqualTo("1")
                .path("events[0].title").entity(String.class).isEqualTo("Music Festival")
                .path("events[0].status").entity(String.class).isEqualTo("PUBLISHED")
                .path("events[0].ticketTypes[0].name").entity(String.class).isEqualTo("General")
                .path("events[0].ticketTypes[0].available").entity(Integer.class).isEqualTo(80);
    }

    @Test
    void event_returnsNotFoundErrorWhenMissing() {
        when(eventService.getEventById(99L)).thenThrow(new ResourceNotFoundException("Event not found"));

        graphQlTester.document("{ event(id: \"99\") { id } }")
                .execute()
                .errors()
                .expect(error -> "Event not found".equals(error.getMessage()))
                .verify();
    }
}
