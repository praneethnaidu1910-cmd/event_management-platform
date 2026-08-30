package com.eventmanagement.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PlatformStatsResponse {
    private long totalUsers;
    private long totalEvents;
    private long publishedEvents;
    private long totalOrders;
    private long activeTicketsSold;
    private BigDecimal totalRevenue;
}
