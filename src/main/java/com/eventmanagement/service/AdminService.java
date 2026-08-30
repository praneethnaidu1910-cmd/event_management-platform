package com.eventmanagement.service;

import com.eventmanagement.dto.response.AdminOrderResponse;
import com.eventmanagement.dto.response.PlatformStatsResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.Order;
import com.eventmanagement.repository.EventRepository;
import com.eventmanagement.repository.OrderRepository;
import com.eventmanagement.repository.TicketRepository;
import com.eventmanagement.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public AdminService(OrderRepository orderRepository, UserRepository userRepository,
                         EventRepository eventRepository, TicketRepository ticketRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toAdminOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlatformStatsResponse getPlatformStats() {
        return PlatformStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalEvents(eventRepository.count())
                .publishedEvents(eventRepository.countByStatus(Event.EventStatus.PUBLISHED))
                .totalOrders(orderRepository.count())
                .activeTicketsSold(ticketRepository.countByStatus("ACTIVE"))
                .totalRevenue(orderRepository.sumRevenue())
                .build();
    }

    private AdminOrderResponse toAdminOrderResponse(Order order) {
        return AdminOrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUser().getId())
                .userEmail(order.getUser().getEmail())
                .eventId(order.getEvent().getId())
                .eventTitle(order.getEvent().getTitle())
                .totalAmount(order.getTotalAmount())
                .status(order.getPaymentStatus())
                .ticketCount(order.getTickets().size())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
