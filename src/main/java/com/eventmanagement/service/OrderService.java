package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.Order;
import com.eventmanagement.entity.Ticket;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.OrderRepository;
import com.eventmanagement.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;

    public OrderService(OrderRepository orderRepository, TicketTypeRepository ticketTypeRepository) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OrderResponse purchaseTickets(PurchaseRequest request, User user) {
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(request.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found"));

        if (ticketType.getAvailable() < request.getQuantity()) {
            throw new InsufficientTicketsException("Not enough tickets available");
        }

        ticketType.setAvailable(ticketType.getAvailable() - request.getQuantity());

        Order order = new Order();
        order.setUser(user);
        order.setEvent(ticketType.getEvent());
        order.setTotalAmount(ticketType.getPrice().multiply(java.math.BigDecimal.valueOf(request.getQuantity())));
        order.setPaymentStatus("COMPLETED");
        order.setCreatedAt(LocalDateTime.now());

        for (int i = 0; i < request.getQuantity(); i++) {
            Ticket ticket = new Ticket();
            ticket.setOrder(order);
            ticket.setTicketType(ticketType);
            ticket.setTicketCode(UUID.randomUUID().toString());
            ticket.setStatus("ACTIVE");
            ticket.setCreatedAt(LocalDateTime.now());
            order.getTickets().add(ticket);
        }

        Order saved = orderRepository.save(order);
        return toOrderResponse(saved);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OrderResponse cancelOrder(Long orderId, User user) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this order");
        }

        if ("CANCELLED".equals(order.getPaymentStatus())) {
            throw new BadRequestException("Order is already cancelled");
        }

        Map<Long, Long> activeCountByTicketTypeId = order.getTickets().stream()
                .filter(ticket -> "ACTIVE".equals(ticket.getStatus()))
                .collect(Collectors.groupingBy(ticket -> ticket.getTicketType().getId(), Collectors.counting()));

        for (Map.Entry<Long, Long> entry : activeCountByTicketTypeId.entrySet()) {
            TicketType ticketType = ticketTypeRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found"));
            ticketType.setAvailable(ticketType.getAvailable() + entry.getValue().intValue());
        }

        order.getTickets().stream()
                .filter(ticket -> "ACTIVE".equals(ticket.getStatus()))
                .forEach(ticket -> ticket.setStatus("CANCELLED"));
        order.setPaymentStatus("CANCELLED");

        Order saved = orderRepository.save(order);
        return toOrderResponse(saved);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<TicketResponse> tickets = order.getTickets().stream()
                .map(ticket -> TicketResponse.builder()
                        .id(ticket.getId())
                        .ticketCode(ticket.getTicketCode())
                        .status(ticket.getStatus())
                        .ticketTypeId(ticket.getTicketType().getId())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getId())
                .totalAmount(order.getTotalAmount())
                .status(order.getPaymentStatus())
                .tickets(tickets)
                .build();
    }
}
