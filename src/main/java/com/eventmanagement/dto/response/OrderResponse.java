package com.eventmanagement.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderResponse {
    private Long orderId;
    private Long eventId;
    private String eventTitle;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private List<TicketResponse> tickets;
}
