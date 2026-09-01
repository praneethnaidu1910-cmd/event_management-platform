package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(EventControllerTest.MethodSecurityConfig.class)
class EventControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private EventRequest validRequest() {
        EventRequest request = new EventRequest();
        request.setTitle("Music Festival");
        request.setLocation("City Park");
        request.setStartDate(LocalDateTime.of(2026, 6, 1, 18, 0));
        request.setEndDate(LocalDateTime.of(2026, 6, 1, 23, 0));
        return request;
    }

    @Test
    @WithMockUser(username = "organizer@example.com", roles = "ORGANIZER")
    void createEvent_returnsCreatedForOrganizer() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);

        EventResponse response = EventResponse.builder()
                .id(1L)
                .organizerId(9L)
                .title("Music Festival")
                .location("City Park")
                .status(Event.EventStatus.DRAFT)
                .build();
        when(eventService.createEvent(any(EventRequest.class), eq(organizer))).thenReturn(response);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Music Festival"));
    }

    @Test
    @WithMockUser(username = "attendee@example.com", roles = "ATTENDEE")
    void createEvent_rejectsNonOrganizerWithForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @WithMockUser(username = "organizer@example.com", roles = "ORGANIZER")
    void createEvent_rejectsBlankTitleWithBadRequest() throws Exception {
        EventRequest request = validRequest();
        request.setTitle("");

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(eventService);
    }

    @Test
    void getEvent_returnsPublishedEventForAnyone() throws Exception {
        EventResponse response = EventResponse.builder()
                .id(1L)
                .organizerId(9L)
                .title("Music Festival")
                .status(Event.EventStatus.PUBLISHED)
                .build();
        when(eventService.getEventById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Music Festival"));
    }

    @Test
    void getEvent_returnsNotFoundForMissingEvent() throws Exception {
        when(eventService.getEventById(404L)).thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/events/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
    }

    @Test
    @WithMockUser(username = "other-organizer@example.com", roles = "ORGANIZER")
    void deleteEvent_rejectsNonOwnerWithForbidden() throws Exception {
        User otherOrganizer = User.builder().id(11L).email("other-organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(otherOrganizer);
        org.mockito.Mockito.doThrow(new AccessDeniedException("Not the event organizer"))
                .when(eventService).deleteEvent(eq(1L), eq(otherOrganizer));

        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not the event organizer"));
    }
}
