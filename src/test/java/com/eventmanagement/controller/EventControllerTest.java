package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.request.TicketTypeRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.GlobalExceptionHandler;
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
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises EventController through MockMvc, the same slice-test pattern
 * used for AuthController. The security filter chain is disabled (as it is
 * there) and CurrentUserService is mocked directly, so these tests cover
 * request mapping, validation and exception translation - not the
 * @PreAuthorize role checks, which live in SecurityConfig and would need a
 * full application context to exercise honestly.
 */
@WebMvcTest(
        controllers = EventController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
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
    void listEvents_returnsPublishedEvents() throws Exception {
        when(eventService.getAllEvents()).thenReturn(List.of(sampleEventResponse()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Tech Conference"));
    }

    @Test
    void searchEvents_passesFiltersToService() throws Exception {
        when(eventService.searchEvents("tech", "CONFERENCE", "Austin", null, null))
                .thenReturn(List.of(sampleEventResponse()));

        mockMvc.perform(get("/api/events/search")
                        .param("keyword", "tech")
                        .param("category", "CONFERENCE")
                        .param("location", "Austin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Tech Conference"));
    }

    @Test
    void getEvent_returnsOkWhenFound() throws Exception {
        when(eventService.getEventById(1L)).thenReturn(sampleEventResponse());

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
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_asOrganizer_returnsCreated() throws Exception {
        User organizer = User.builder().id(7L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.createEvent(any(EventRequest.class), eq(organizer))).thenReturn(sampleEventResponse());

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Tech Conference"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_rejectsInvalidPayloadWithBadRequest() throws Exception {
        EventRequest request = validEventRequest();
        request.setTitle("");
        request.setLocation(null);

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_asOwningOrganizer_returnsOk() throws Exception {
        User organizer = User.builder().id(7L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), eq(organizer))).thenReturn(sampleEventResponse());

        mockMvc.perform(put("/api/events/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tech Conference"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_whenNotOwner_returnsForbidden() throws Exception {
        User otherOrganizer = User.builder().id(8L).email("other@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(otherOrganizer);
        when(eventService.updateEvent(eq(1L), any(EventRequest.class), eq(otherOrganizer)))
                .thenThrow(new AccessDeniedException("Not the event organizer"));

        mockMvc.perform(put("/api/events/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validEventRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not the event organizer"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteEvent_asOwningOrganizer_returnsNoContent() throws Exception {
        User organizer = User.builder().id(7L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);

        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isNoContent());

        verify(eventService).deleteEvent(1L, organizer);
    }

    private EventRequest validEventRequest() {
        EventRequest request = new EventRequest();
        request.setTitle("Tech Conference");
        request.setDescription("A conference about tech");
        request.setLocation("Austin");
        request.setVenueName("Convention Center");
        request.setStartDate(LocalDateTime.now().plusDays(30));
        request.setEndDate(LocalDateTime.now().plusDays(31));
        request.setStatus(Event.EventStatus.DRAFT);
        request.setCategory("CONFERENCE");
        request.setMaxAttendees(500);

        TicketTypeRequest ticketType = new TicketTypeRequest();
        ticketType.setName("General");
        ticketType.setPrice(BigDecimal.valueOf(99.99));
        ticketType.setQuantity(100);
        request.setTicketTypes(List.of(ticketType));
        return request;
    }

    private EventResponse sampleEventResponse() {
        return EventResponse.builder()
                .id(1L)
                .organizerId(7L)
                .title("Tech Conference")
                .description("A conference about tech")
                .location("Austin")
                .venueName("Convention Center")
                .startDate(LocalDateTime.now().plusDays(30))
                .endDate(LocalDateTime.now().plusDays(31))
                .status(Event.EventStatus.DRAFT)
                .category("CONFERENCE")
                .maxAttendees(500)
                .ticketTypes(List.of())
                .build();
    }
}
