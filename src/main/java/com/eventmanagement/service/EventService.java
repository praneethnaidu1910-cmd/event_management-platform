package com.eventmanagement.service;

import com.eventmanagement.dto.request.EventRequest;
import com.eventmanagement.dto.request.TicketTypeRequest;
import com.eventmanagement.dto.response.EventResponse;
import com.eventmanagement.dto.response.TicketTypeResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventResponse createEvent(EventRequest request, User organizer) {
        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setVenueName(request.getVenueName());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        event.setStatus(request.getStatus() != null ? request.getStatus() : Event.EventStatus.DRAFT);
        event.setCategory(request.getCategory());
        event.setMaxAttendees(request.getMaxAttendees());
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());

        if (request.getTicketTypes() != null) {
            for (TicketTypeRequest ticketTypeRequest : request.getTicketTypes()) {
                TicketType ticketType = new TicketType();
                ticketType.setEvent(event);
                ticketType.setName(ticketTypeRequest.getName());
                ticketType.setDescription(ticketTypeRequest.getDescription());
                ticketType.setPrice(ticketTypeRequest.getPrice());
                ticketType.setQuantity(ticketTypeRequest.getQuantity());
                ticketType.setAvailable(ticketTypeRequest.getQuantity());
                ticketType.setCreatedAt(LocalDateTime.now());
                event.getTicketTypes().add(ticketType);
            }
        }

        Event saved = eventRepository.save(event);
        return toEventResponse(saved);
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findByStatus(Event.EventStatus.PUBLISHED).stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    public Page<EventResponse> getAllEvents(int page, int size) {
        Pageable pageable = pageableSortedByStartDate(page, size);
        return eventRepository.findByStatus(Event.EventStatus.PUBLISHED, pageable)
                .map(this::toEventResponse);
    }

    public List<EventResponse> searchEvents(String keyword, String category, String location,
                                             LocalDateTime startFrom, LocalDateTime startTo) {
        return eventRepository.search(
                        Event.EventStatus.PUBLISHED,
                        normalize(keyword),
                        normalize(category),
                        normalize(location),
                        startFrom,
                        startTo
                ).stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    public Page<EventResponse> searchEvents(String keyword, String category, String location,
                                             LocalDateTime startFrom, LocalDateTime startTo,
                                             int page, int size) {
        Pageable pageable = pageableSortedByStartDate(page, size);
        return eventRepository.search(
                        Event.EventStatus.PUBLISHED,
                        normalize(keyword),
                        normalize(category),
                        normalize(location),
                        startFrom,
                        startTo,
                        pageable
                )
                .map(this::toEventResponse);
    }

    private Pageable pageableSortedByStartDate(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by("startDate").ascending());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (event.getStatus() != Event.EventStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Event not found");
        }
        return toEventResponse(event);
    }

    public EventResponse updateEvent(Long id, EventRequest request, User organizer) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new AccessDeniedException("Not the event organizer");
        }

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setVenueName(request.getVenueName());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        event.setStatus(request.getStatus() != null ? request.getStatus() : event.getStatus());
        event.setCategory(request.getCategory());
        event.setMaxAttendees(request.getMaxAttendees());
        event.setUpdatedAt(LocalDateTime.now());

        event.getTicketTypes().clear();
        if (request.getTicketTypes() != null) {
            for (TicketTypeRequest ticketTypeRequest : request.getTicketTypes()) {
                TicketType ticketType = new TicketType();
                ticketType.setEvent(event);
                ticketType.setName(ticketTypeRequest.getName());
                ticketType.setDescription(ticketTypeRequest.getDescription());
                ticketType.setPrice(ticketTypeRequest.getPrice());
                ticketType.setQuantity(ticketTypeRequest.getQuantity());
                ticketType.setAvailable(ticketTypeRequest.getQuantity());
                ticketType.setCreatedAt(LocalDateTime.now());
                event.getTicketTypes().add(ticketType);
            }
        }

        Event saved = eventRepository.save(event);
        return toEventResponse(saved);
    }

    public void deleteEvent(Long id, User organizer) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new AccessDeniedException("Not the event organizer");
        }
        eventRepository.delete(event);
    }

    private EventResponse toEventResponse(Event event) {
        List<TicketTypeResponse> ticketTypes = event.getTicketTypes().stream()
                .map(ticketType -> TicketTypeResponse.builder()
                        .id(ticketType.getId())
                        .name(ticketType.getName())
                        .description(ticketType.getDescription())
                        .price(ticketType.getPrice())
                        .quantity(ticketType.getQuantity())
                        .available(ticketType.getAvailable())
                        .build())
                .collect(Collectors.toList());

        return EventResponse.builder()
                .id(event.getId())
                .organizerId(event.getOrganizer().getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .venueName(event.getVenueName())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .status(event.getStatus())
                .category(event.getCategory())
                .maxAttendees(event.getMaxAttendees())
                .ticketTypes(ticketTypes)
                .build();
    }
}
