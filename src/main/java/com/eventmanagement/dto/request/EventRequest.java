package com.eventmanagement.dto.request;

import com.eventmanagement.entity.Event;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EventRequest {
    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String location;

    private String venueName;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    private Event.EventStatus status;

    private String category;

    private Integer maxAttendees;

    @Valid
    private List<TicketTypeRequest> ticketTypes = new ArrayList<>();
}
