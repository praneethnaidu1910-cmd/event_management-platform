package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.EventOrderResponse;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.Order;
import com.eventmanagement.entity.Ticket;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.AccessDeniedException;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.EventRepository;
import com.eventmanagement.repository.OrderRepository;
import com.eventmanagement.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;

    public OrderService(OrderRepository orderRepository, TicketTypeRepository ticketTypeRepository,
                         EventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
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

    public List<OrderResponse> getOrdersForUser(User user) {
        return orderRepository.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    public List<EventOrderResponse> getOrdersForEvent(Long eventId, User requester) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        boolean isOrganizerOfEvent = event.getOrganizer().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == User.Role.ADMIN;
        if (!isOrganizerOfEvent && !isAdmin) {
            throw new AccessDeniedException("Not the event organizer");
        }

        return orderRepository.findByEventId(eventId).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .map(this::toEventOrderResponse)
                .collect(Collectors.toList());
    }

    private EventOrderResponse toEventOrderResponse(Order order) {
        List<TicketResponse> tickets = order.getTickets().stream()
                .map(ticket -> TicketResponse.builder()
                        .id(ticket.getId())
                        .ticketCode(ticket.getTicketCode())
                        .status(ticket.getStatus())
                        .ticketTypeId(ticket.getTicketType().getId())
                        .build())
                .collect(Collectors.toList());

        User buyer = order.getUser();
        return EventOrderResponse.builder()
                .orderId(order.getId())
                .buyerId(buyer.getId())
                .buyerName(buyer.getFirstName() + " " + buyer.getLastName())
                .buyerEmail(buyer.getEmail())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .tickets(tickets)
                .build();
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
                .eventId(order.getEvent().getId())
                .eventTitle(order.getEvent().getTitle())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .tickets(tickets)
                .build();
    }
}
