package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private CurrentUserService currentUserService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User organizer(long id) {
        return User.builder().id(id).email("organizer@example.com").role(User.Role.ORGANIZER).build();
    }

    private EventResponse eventResponse(long id) {
        return EventResponse.builder()
                .id(id)
                .organizerId(1L)
                .title("Music Festival")
                .location("City Park")
                .startDate(LocalDateTime.of(2026, 6, 1, 18, 0))
                .endDate(LocalDateTime.of(2026, 6, 1, 23, 0))
                .status(Event.EventStatus.PUBLISHED)
                .build();
    }

    private String eventRequestJson() throws Exception {
        EventRequest request = new EventRequest();
        request.setTitle("Music Festival");
        request.setLocation("City Park");
        request.setStartDate(LocalDateTime.of(2026, 6, 1, 18, 0));
        request.setEndDate(LocalDateTime.of(2026, 6, 1, 23, 0));
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void listEvents_isPublic() throws Exception {
        when(eventService.getAllEvents()).thenReturn(java.util.List.of(eventResponse(1L)));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Music Festival"));
    }

    @Test
    void getEvent_returnsEventWhenFound() throws Exception {
        when(eventService.getEventById(1L)).thenReturn(eventResponse(1L));

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getEvent_returnsNotFoundWhenMissing() throws Exception {
        when(eventService.getEventById(99L)).thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
    }

    @Test
    void searchEvents_passesFiltersToService() throws Exception {
        when(eventService.searchEvents(eq("jazz"), isNull(), eq("Boston"), isNull(), isNull()))
                .thenReturn(java.util.List.of(eventResponse(2L)));

        mockMvc.perform(get("/api/events/search").param("keyword", "jazz").param("location", "Boston"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_asOrganizer_returnsCreated() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(organizer(1L));
        when(eventService.createEvent(any(EventRequest.class), any(User.class))).thenReturn(eventResponse(5L));

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(eventRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void createEvent_asNonOrganizer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(eventRequestJson()))
                .andExpect(status().isForbidden());

        verify(eventService, never()).createEvent(any(), any());
    }

    @Test
    void createEvent_unauthenticated_isForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(eventRequestJson()))
                .andExpect(status().isForbidden());

        verify(eventService, never()).createEvent(any(), any());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_asOrganizer_returnsOk() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(organizer(1L));
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), any(User.class))).thenReturn(eventResponse(1L));

        mockMvc.perform(put("/api/events/1")
                        .contentType("application/json")
                        .content(eventRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_notOwnedByOrganizer_isForbidden() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(organizer(1L));
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), any(User.class)))
                .thenThrow(new AccessDeniedException("Not the event organizer"));

        mockMvc.perform(put("/api/events/1")
                        .contentType("application/json")
                        .content(eventRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not the event organizer"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteEvent_asOrganizer_returnsNoContent() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(organizer(1L));

        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isNoContent());

        verify(eventService).deleteEvent(eq(1L), any(User.class));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void deleteEvent_asNonOrganizer_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isForbidden());

        verify(eventService, never()).deleteEvent(anyLong(), any());
    }
}
