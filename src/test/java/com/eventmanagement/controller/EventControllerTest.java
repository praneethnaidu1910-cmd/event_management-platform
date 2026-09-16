package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.request.TicketTypeRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.dto.response.TicketTypeResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
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
    private UserDetailsServiceImpl userDetailsService;

    private EventRequest validEventRequest() {
        EventRequest request = new EventRequest();
        request.setTitle("Music Festival");
        request.setLocation("City Park");
        request.setStartDate(LocalDateTime.of(2026, 6, 1, 18, 0));
        request.setEndDate(LocalDateTime.of(2026, 6, 1, 23, 0));

        TicketTypeRequest ticketType = new TicketTypeRequest();
        ticketType.setName("General");
        ticketType.setPrice(new BigDecimal("50.00"));
        ticketType.setQuantity(100);
        request.setTicketTypes(List.of(ticketType));

        return request;
    }

    private EventResponse eventResponse() {
        return EventResponse.builder()
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
                                .available(100)
                                .build()
                ))
                .build();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_asOrganizer_returnsCreated() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.createEvent(any(EventRequest.class), eq(organizer))).thenReturn(eventResponse());

        mockMvc.perform(post("/api/events")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Music Festival"))
                .andExpect(jsonPath("$.ticketTypes[0].name").value("General"));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void createEvent_asAttendee_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEvent_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_missingRequiredFields_returnsBadRequest() throws Exception {
        EventRequest request = new EventRequest();

        mockMvc.perform(post("/api/events")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listEvents_isPublic_returnsOk() throws Exception {
        when(eventService.getAllEvents()).thenReturn(List.of(eventResponse()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Music Festival"));
    }

    @Test
    void getEvent_isPublic_returnsOk() throws Exception {
        when(eventService.getEventById(1L)).thenReturn(eventResponse());

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getEvent_notFound_returnsNotFound() throws Exception {
        when(eventService.getEventById(99L)).thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_asOwner_returnsOk() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), eq(organizer))).thenReturn(eventResponse());

        mockMvc.perform(put("/api/events/1")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_notOwner_returnsForbidden() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), eq(organizer)))
                .thenThrow(new AccessDeniedException("Not the event organizer"));

        mockMvc.perform(put("/api/events/1")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void updateEvent_asAttendee_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/events/1")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteEvent_asOwner_returnsNoContent() throws Exception {
        User organizer = User.builder().id(9L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);

        mockMvc.perform(delete("/api/events/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void deleteEvent_asAttendee_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/events/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteEvent_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/events/1").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
