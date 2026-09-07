package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security is disabled for this slice test: it exercises the controller's own
 * request handling (routing, param binding, validation, exception mapping),
 * not the {@code @PreAuthorize} rules enforced by SecurityConfig.
 */
@WebMvcTest(
        controllers = EventController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void listEventsReturnsPublishedEvents() throws Exception {
        EventResponse response = sampleEvent(1L);
        when(eventService.getAllEvents()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Tech Conference"));
    }

    @Test
    void getEventReturnsEventWhenFound() throws Exception {
        when(eventService.getEventById(1L)).thenReturn(sampleEvent(1L));

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getEventReturns404WhenMissing() throws Exception {
        when(eventService.getEventById(99L)).thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
    }

    @Test
    void searchEventsPassesParsedParamsToService() throws Exception {
        when(eventService.searchEvents(any(), any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/events/search")
                        .param("keyword", "music")
                        .param("category", "Concert")
                        .param("startFrom", "2026-10-01T00:00:00"))
                .andExpect(status().isOk());

        verify(eventService).searchEvents(
                eq("music"),
                eq("Concert"),
                isNull(),
                eq(LocalDateTime.parse("2026-10-01T00:00:00")),
                isNull());
    }

    @Test
    void createEventRejectsBlankTitle() throws Exception {
        EventRequest request = new EventRequest();
        request.setTitle("");
        request.setLocation("Main Hall");
        request.setStartDate(LocalDateTime.now().plusDays(1));
        request.setEndDate(LocalDateTime.now().plusDays(2));

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: must not be blank"));
    }

    private EventResponse sampleEvent(Long id) {
        return EventResponse.builder()
                .id(id)
                .organizerId(10L)
                .title("Tech Conference")
                .description("Annual tech conference")
                .location("Downtown Hall")
                .startDate(LocalDateTime.now().plusDays(30))
                .endDate(LocalDateTime.now().plusDays(31))
                .status(Event.EventStatus.PUBLISHED)
                .category("Technology")
                .ticketTypes(List.of())
                .build();
    }
}
