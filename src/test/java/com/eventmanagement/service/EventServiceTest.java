package com.eventmanagement.service;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.request.TicketTypeRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private EventRepository eventRepository;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        eventService = new EventService(eventRepository);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User organizer(long id) {
        return User.builder().id(id).email("organizer@example.com").role(User.Role.ORGANIZER).build();
    }

    private EventRequest eventRequest() {
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
    void createEvent_defaultsToDraftAndSeedsTicketAvailabilityFromQuantity() {
        EventRequest request = eventRequest();

        EventResponse response = eventService.createEvent(request, organizer(1L));

        assertThat(response.getStatus()).isEqualTo(Event.EventStatus.DRAFT);
        assertThat(response.getOrganizerId()).isEqualTo(1L);
        assertThat(response.getTicketTypes()).hasSize(1);
        assertThat(response.getTicketTypes().get(0).getAvailable()).isEqualTo(100);
    }

    @Test
    void createEvent_honorsExplicitStatus() {
        EventRequest request = eventRequest();
        request.setStatus(Event.EventStatus.PUBLISHED);

        EventResponse response = eventService.createEvent(request, organizer(1L));

        assertThat(response.getStatus()).isEqualTo(Event.EventStatus.PUBLISHED);
    }

    @Test
    void getEventById_returnsPublishedEvent() {
        Event event = Event.builder()
                .id(7L)
                .organizer(organizer(1L))
                .title("Music Festival")
                .location("City Park")
                .status(Event.EventStatus.PUBLISHED)
                .ticketTypes(List.of())
                .build();
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEventById(7L);

        assertThat(response.getId()).isEqualTo(7L);
    }

    @Test
    void getEventById_hidesUnpublishedEventFromPublicLookup() {
        Event event = Event.builder()
                .id(7L)
                .organizer(organizer(1L))
                .title("Music Festival")
                .location("City Park")
                .status(Event.EventStatus.DRAFT)
                .ticketTypes(List.of())
                .build();
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.getEventById(7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateEvent_rejectsNonOwningOrganizer() {
        Event event = Event.builder()
                .id(7L)
                .organizer(organizer(1L))
                .title("Music Festival")
                .location("City Park")
                .status(Event.EventStatus.DRAFT)
                .ticketTypes(new java.util.ArrayList<>())
                .build();
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.updateEvent(7L, eventRequest(), organizer(2L)))
                .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void deleteEvent_rejectsNonOwningOrganizer() {
        Event event = Event.builder()
                .id(7L)
                .organizer(organizer(1L))
                .title("Music Festival")
                .location("City Park")
                .status(Event.EventStatus.DRAFT)
                .build();
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.deleteEvent(7L, organizer(2L)))
                .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).delete(any());
    }

    @Test
    void deleteEvent_allowsOwningOrganizer() {
        Event event = Event.builder()
                .id(7L)
                .organizer(organizer(1L))
                .title("Music Festival")
                .location("City Park")
                .status(Event.EventStatus.DRAFT)
                .build();
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));

        eventService.deleteEvent(7L, organizer(1L));

        verify(eventRepository).delete(event);
    }

    @Test
    void searchEvents_delegatesToRepositoryWithPublishedStatus() {
        when(eventRepository.search(eq(Event.EventStatus.PUBLISHED), eq("jazz"), eq("Music"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        eventService.searchEvents("jazz", "Music", null, null, null);

        verify(eventRepository).search(Event.EventStatus.PUBLISHED, "jazz", "Music", null, null, null);
    }

    @Test
    void searchEvents_normalizesBlankFiltersToNull() {
        when(eventRepository.search(eq(Event.EventStatus.PUBLISHED), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        eventService.searchEvents("   ", "", null, null, null);

        verify(eventRepository).search(Event.EventStatus.PUBLISHED, null, null, null, null, null);
    }
}
