package com.eventmanagement.dto.response;

import com.eventmanagement.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class EventResponse {
    private Long id;
    private Long organizerId;
    private String title;
    private String description;
    private String location;
    private String venueName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Event.EventStatus status;
    private String category;
    private Integer maxAttendees;
    private List<TicketTypeResponse> ticketTypes;
}
