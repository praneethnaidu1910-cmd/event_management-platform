package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.EventOrderResponse;
import com.eventmanagement.dto.response.OrderResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private TicketTypeRepository ticketTypeRepository;
    private EventRepository eventRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        eventRepository = mock(EventRepository.class);
        orderService = new OrderService(orderRepository, ticketTypeRepository, eventRepository);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TicketType ticketType(int available, String price) {
        return TicketType.builder()
                .id(1L)
                .event(Event.builder().id(10L).build())
                .name("General")
                .price(new BigDecimal(price))
                .quantity(100)
                .available(available)
                .build();
    }

    private PurchaseRequest purchaseRequest(long ticketTypeId, int quantity) {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(ticketTypeId);
        request.setQuantity(quantity);
        return request;
    }

    @Test
    void purchaseTickets_decrementsAvailabilityAndCreatesOneTicketPerQuantity() {
        TicketType ticketType = ticketType(10, "25.00");
        when(ticketTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticketType));
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();

        OrderResponse response = orderService.purchaseTickets(purchaseRequest(1L, 3), buyer);

        assertThat(ticketType.getAvailable()).isEqualTo(7);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("75.00");
        assertThat(response.getTickets()).hasSize(3);
        assertThat(response.getTickets()).allSatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo("ACTIVE");
            assertThat(ticket.getTicketCode()).isNotBlank();
        });
        assertThat(response.getTickets().stream().map(t -> t.getTicketCode()).distinct().count()).isEqualTo(3);
    }

    @Test
    void purchaseTickets_rejectsQuantityAboveAvailability() {
        TicketType ticketType = ticketType(2, "25.00");
        when(ticketTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticketType));
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();

        assertThatThrownBy(() -> orderService.purchaseTickets(purchaseRequest(1L, 3), buyer))
                .isInstanceOf(InsufficientTicketsException.class);

        assertThat(ticketType.getAvailable()).isEqualTo(2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void purchaseTickets_throwsWhenTicketTypeDoesNotExist() {
        when(ticketTypeRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();

        assertThatThrownBy(() -> orderService.purchaseTickets(purchaseRequest(99L, 1), buyer))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrdersForUser_returnsOrdersNewestFirstWithEventAndTicketDetails() {
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        Event event = Event.builder().id(10L).title("Tech Conference").build();

        Order older = Order.builder()
                .id(1L)
                .user(buyer)
                .event(event)
                .totalAmount(new BigDecimal("25.00"))
                .paymentStatus("COMPLETED")
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .tickets(new java.util.ArrayList<>())
                .build();
        older.getTickets().add(Ticket.builder()
                .id(100L)
                .order(older)
                .ticketType(TicketType.builder().id(1L).build())
                .ticketCode("code-1")
                .status("ACTIVE")
                .build());

        Order newer = Order.builder()
                .id(2L)
                .user(buyer)
                .event(event)
                .totalAmount(new BigDecimal("50.00"))
                .paymentStatus("COMPLETED")
                .createdAt(LocalDateTime.of(2026, 8, 15, 10, 0))
                .tickets(new java.util.ArrayList<>())
                .build();

        when(orderRepository.findByUserId(5L)).thenReturn(List.of(older, newer));

        List<OrderResponse> responses = orderService.getOrdersForUser(buyer);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getOrderId()).isEqualTo(2L);
        assertThat(responses.get(1).getOrderId()).isEqualTo(1L);
        assertThat(responses.get(1).getEventId()).isEqualTo(10L);
        assertThat(responses.get(1).getEventTitle()).isEqualTo("Tech Conference");
        assertThat(responses.get(1).getPaymentStatus()).isEqualTo("COMPLETED");
        assertThat(responses.get(1).getTickets()).hasSize(1);
        assertThat(responses.get(1).getTickets().get(0).getTicketCode()).isEqualTo("code-1");
    }

    @Test
    void getOrdersForUser_returnsEmptyListWhenUserHasNoOrders() {
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        when(orderRepository.findByUserId(5L)).thenReturn(List.of());

        assertThat(orderService.getOrdersForUser(buyer)).isEmpty();
    }

    @Test
    void getOrdersForEvent_returnsOrdersWithBuyerDetailsForTheOwningOrganizer() {
        User organizer = User.builder().id(20L).email("organizer@example.com").role(User.Role.ORGANIZER).build();
        Event event = Event.builder().id(10L).organizer(organizer).title("Tech Conference").build();
        User buyer = User.builder().id(5L).email("buyer@example.com").firstName("Jane").lastName("Doe")
                .role(User.Role.ATTENDEE).build();

        Order order = Order.builder()
                .id(1L)
                .user(buyer)
                .event(event)
                .totalAmount(new BigDecimal("50.00"))
                .paymentStatus("COMPLETED")
                .createdAt(LocalDateTime.of(2026, 8, 15, 10, 0))
                .tickets(new java.util.ArrayList<>())
                .build();
        order.getTickets().add(Ticket.builder()
                .id(100L)
                .order(order)
                .ticketType(TicketType.builder().id(1L).build())
                .ticketCode("code-1")
                .status("ACTIVE")
                .build());

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(orderRepository.findByEventId(10L)).thenReturn(List.of(order));

        List<EventOrderResponse> responses = orderService.getOrdersForEvent(10L, organizer);

        assertThat(responses).hasSize(1);
        EventOrderResponse response = responses.get(0);
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getBuyerId()).isEqualTo(5L);
        assertThat(response.getBuyerName()).isEqualTo("Jane Doe");
        assertThat(response.getBuyerEmail()).isEqualTo("buyer@example.com");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getTickets()).hasSize(1);
        assertThat(response.getTickets().get(0).getTicketCode()).isEqualTo("code-1");
    }

    @Test
    void getOrdersForEvent_allowsAdminRegardlessOfOwnership() {
        User organizer = User.builder().id(20L).role(User.Role.ORGANIZER).build();
        User admin = User.builder().id(99L).role(User.Role.ADMIN).build();
        Event event = Event.builder().id(10L).organizer(organizer).build();

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(orderRepository.findByEventId(10L)).thenReturn(List.of());

        assertThat(orderService.getOrdersForEvent(10L, admin)).isEmpty();
    }

    @Test
    void getOrdersForEvent_rejectsRequesterWhoDoesNotOwnTheEvent() {
        User organizer = User.builder().id(20L).role(User.Role.ORGANIZER).build();
        User otherOrganizer = User.builder().id(21L).role(User.Role.ORGANIZER).build();
        Event event = Event.builder().id(10L).organizer(organizer).build();

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> orderService.getOrdersForEvent(10L, otherOrganizer))
                .isInstanceOf(AccessDeniedException.class);

        verify(orderRepository, never()).findByEventId(any());
    }

    @Test
    void getOrdersForEvent_throwsWhenEventDoesNotExist() {
        User organizer = User.builder().id(20L).role(User.Role.ORGANIZER).build();
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrdersForEvent(99L, organizer))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
