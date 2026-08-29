package com.eventmanagement.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class EventOrderResponse {
    private Long orderId;
    private Long buyerId;
    private String buyerName;
    private String buyerEmail;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private List<TicketResponse> tickets;
}
