package com.eventmanagement.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketResponse {
    private Long id;
    private String ticketCode;
    private String status;
    private Long ticketTypeId;
}
