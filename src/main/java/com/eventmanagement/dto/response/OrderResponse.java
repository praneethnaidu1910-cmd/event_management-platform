package com.eventmanagement.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class OrderResponse {
    private Long orderId;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private List<TicketResponse> tickets;
}
