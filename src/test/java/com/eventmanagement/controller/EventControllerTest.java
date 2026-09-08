package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.security.JwtTokenProvider;
import com.eventmanagement.security.SecurityConfig;
import com.eventmanagement.security.UserDetailsServiceImpl;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private EventRequest validEventRequest() {
        EventRequest request = new EventRequest();
        request.setTitle("Music Festival");
        request.setLocation("City Park");
        request.setStartDate(LocalDateTime.of(2026, 6, 1, 18, 0));
        request.setEndDate(LocalDateTime.of(2026, 6, 1, 23, 0));
        return request;
    }

    private EventResponse sampleEventResponse() {
        return EventResponse.builder()
                .id(1L)
                .organizerId(9L)
                .title("Music Festival")
                .location("City Park")
                .startDate(LocalDateTime.of(2026, 6, 1, 18, 0))
                .endDate(LocalDateTime.of(2026, 6, 1, 23, 0))
                .status(Event.EventStatus.PUBLISHED)
                .build();
    }

    @Test
    void listEvents_isPubliclyAccessible() throws Exception {
        when(eventService.getAllEvents()).thenReturn(List.of(sampleEventResponse()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Music Festival"));
    }

    @Test
    void getEvent_returns404WhenMissing() throws Exception {
        when(eventService.getEventById(99L)).thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_asOrganizer_returns201() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.createEvent(any(EventRequest.class), eq(organizer))).thenReturn(sampleEventResponse());

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void createEvent_asAttendee_returns403() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEvent_whenAnonymous_returns403() throws Exception {
        // No AuthenticationEntryPoint is configured, so Spring Security falls back to
        // Http403ForbiddenEntryPoint for unauthenticated requests rather than a 401.
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_missingTitle_returns400() throws Exception {
        EventRequest request = validEventRequest();
        request.setTitle(" ");

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_notOwner_returns403() throws Exception {
        User someoneElse = User.builder().id(2L).email("other@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(someoneElse);
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), eq(someoneElse)))
                .thenThrow(new AccessDeniedException("Not the event organizer"));

        mockMvc.perform(put("/api/events/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not the event organizer"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteEvent_asOrganizer_returns204() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);

        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void deleteEvent_asAttendee_returns403() throws Exception {
        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isForbidden());
    }
}
