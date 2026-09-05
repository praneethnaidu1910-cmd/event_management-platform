package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.Order;
import com.eventmanagement.entity.Ticket;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.OrderRepository;
import com.eventmanagement.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final NotificationService notificationService;

    public OrderService(
            OrderRepository orderRepository,
            TicketTypeRepository ticketTypeRepository,
            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.notificationService = notificationService;
    }

    /**
     * findByIdForUpdate() already takes a pessimistic write lock on the ticket
     * type row, which is what actually prevents overselling by serializing
     * concurrent purchases of the same ticket type. Layering SERIALIZABLE
     * isolation on top of that lock was redundant, and under real concurrent
     * load it made the database reject transactions outright with spurious
     * serialization failures instead of just queuing them behind the row lock.
     */
    @Transactional
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

        notificationService.sendPurchaseConfirmation(saved);

        List<TicketResponse> tickets = saved.getTickets().stream()
                .map(ticket -> TicketResponse.builder()
                        .id(ticket.getId())
                        .ticketCode(ticket.getTicketCode())
                        .status(ticket.getStatus())
                        .ticketTypeId(ticket.getTicketType().getId())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(saved.getId())
                .totalAmount(saved.getTotalAmount())
                .tickets(tickets)
                .build();
    }
}
