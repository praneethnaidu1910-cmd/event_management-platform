package com.eventmanagement.controller;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.request.TicketTypeRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.repository.UserRepository;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.security.JwtTokenProvider;
import com.eventmanagement.security.SecurityConfig;
import com.eventmanagement.security.UserDetailsServiceImpl;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockBean
    private EventService eventService;

    @MockBean
    private CurrentUserService currentUserService;

    // Pulled in transitively by SecurityConfig's JwtAuthenticationFilter bean; unused by these
    // tests since @WithMockUser seeds the SecurityContext directly, bypassing the filter.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    private EventResponse sampleResponse() {
        return EventResponse.builder()
                .id(7L)
                .organizerId(1L)
                .title("Music Festival")
                .location("City Park")
                .startDate(LocalDateTime.now().plusDays(30))
                .endDate(LocalDateTime.now().plusDays(30).plusHours(5))
                .status(Event.EventStatus.DRAFT)
                .ticketTypes(List.of())
                .build();
    }

    private EventRequest sampleRequest() {
        EventRequest request = new EventRequest();
        request.setTitle("Music Festival");
        request.setLocation("City Park");
        request.setStartDate(LocalDateTime.now().plusDays(30));
        request.setEndDate(LocalDateTime.now().plusDays(30).plusHours(5));

        TicketTypeRequest general = new TicketTypeRequest();
        general.setName("General");
        general.setPrice(new BigDecimal("50.00"));
        general.setQuantity(100);
        request.setTicketTypes(List.of(general));
        return request;
    }

    @Test
    void listEvents_isPubliclyAccessible() throws Exception {
        when(eventService.getAllEvents()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].title").value("Music Festival"));
    }

    @Test
    void getEvent_isPubliclyAccessible() throws Exception {
        when(eventService.getEventById(7L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/events/{id}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void searchEvents_forwardsFiltersToService() throws Exception {
        when(eventService.searchEvents("jazz", "Music", "Park", null, null))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/events/search")
                        .param("keyword", "jazz")
                        .param("category", "Music")
                        .param("location", "Park"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));

        verify(eventService).searchEvents("jazz", "Music", "Park", null, null);
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_asOrganizer_returnsCreated() throws Exception {
        User organizer = User.builder().id(1L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.createEvent(any(EventRequest.class), eq(organizer))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));

        verify(eventService, times(1)).createEvent(any(EventRequest.class), eq(organizer));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void createEvent_asAttendee_isForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());

        verify(eventService, never()).createEvent(any(), any());
    }

    @Test
    void createEvent_withoutAuthentication_isForbidden() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());

        verify(eventService, never()).createEvent(any(), any());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void updateEvent_asOrganizer_delegatesToService() throws Exception {
        User organizer = User.builder().id(1L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);
        when(eventService.updateEvent(eq(7L), any(EventRequest.class), eq(organizer))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/events/{id}", 7L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteEvent_asOrganizer_returnsNoContent() throws Exception {
        User organizer = User.builder().id(1L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        when(currentUserService.getCurrentUser()).thenReturn(organizer);

        mockMvc.perform(delete("/api/events/{id}", 7L))
                .andExpect(status().isNoContent());

        verify(eventService).deleteEvent(7L, organizer);
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void deleteEvent_asAttendee_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/events/{id}", 7L))
                .andExpect(status().isForbidden());

        verify(eventService, never()).deleteEvent(anyLong(), any());
    }
}
