package com.eventmanagement.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdminOrderResponse {
    private Long orderId;
    private Long userId;
    private String userEmail;
    private Long eventId;
    private String eventTitle;
    private BigDecimal totalAmount;
    private String status;
    private int ticketCount;
    private LocalDateTime createdAt;
}
