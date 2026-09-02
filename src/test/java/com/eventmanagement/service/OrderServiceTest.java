package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.entity.Event;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        orderService = new OrderService(orderRepository, ticketTypeRepository);
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

    private Order orderWithTickets(User buyer, Event event, TicketType ticketType, int ticketCount, String paymentStatus) {
        Order order = Order.builder()
                .id(1L)
                .user(buyer)
                .event(event)
                .totalAmount(ticketType.getPrice().multiply(BigDecimal.valueOf(ticketCount)))
                .paymentStatus(paymentStatus)
                .createdAt(LocalDateTime.now())
                .tickets(new ArrayList<>())
                .build();

        for (int i = 0; i < ticketCount; i++) {
            Ticket ticket = Ticket.builder()
                    .id((long) (i + 1))
                    .order(order)
                    .ticketType(ticketType)
                    .ticketCode("code-" + i)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();
            order.getTickets().add(ticket);
        }
        return order;
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
    void cancelOrder_restoresAvailabilityAndMarksTicketsCancelled() {
        Event event = Event.builder().id(10L).startDate(LocalDateTime.now().plusDays(5)).build();
        TicketType ticketType = ticketType(7, "25.00");
        ticketType.setEvent(event);
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        Order order = orderWithTickets(buyer, event, ticketType, 3, "COMPLETED");

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(ticketTypeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticketType));

        OrderResponse response = orderService.cancelOrder(1L, buyer);

        assertThat(ticketType.getAvailable()).isEqualTo(10);
        assertThat(order.getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(response.getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(response.getTickets()).hasSize(3);
        assertThat(response.getTickets()).allSatisfy(ticket -> assertThat(ticket.getStatus()).isEqualTo("CANCELLED"));
    }

    @Test
    void cancelOrder_rejectsWhenCallerDoesNotOwnTheOrder() {
        Event event = Event.builder().id(10L).startDate(LocalDateTime.now().plusDays(5)).build();
        TicketType ticketType = ticketType(7, "25.00");
        ticketType.setEvent(event);
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        User otherUser = User.builder().id(6L).email("other@example.com").role(User.Role.ATTENDEE).build();
        Order order = orderWithTickets(buyer, event, ticketType, 3, "COMPLETED");

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, otherUser))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(ticketType.getAvailable()).isEqualTo(7);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_rejectsWhenAlreadyCancelled() {
        Event event = Event.builder().id(10L).startDate(LocalDateTime.now().plusDays(5)).build();
        TicketType ticketType = ticketType(7, "25.00");
        ticketType.setEvent(event);
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        Order order = orderWithTickets(buyer, event, ticketType, 3, "REFUNDED");

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, buyer))
                .isInstanceOf(BadRequestException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_rejectsWhenEventAlreadyStarted() {
        Event event = Event.builder().id(10L).startDate(LocalDateTime.now().minusHours(1)).build();
        TicketType ticketType = ticketType(7, "25.00");
        ticketType.setEvent(event);
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        Order order = orderWithTickets(buyer, event, ticketType, 3, "COMPLETED");

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, buyer))
                .isInstanceOf(BadRequestException.class);

        assertThat(ticketType.getAvailable()).isEqualTo(7);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_throwsWhenOrderDoesNotExist() {
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();

        assertThatThrownBy(() -> orderService.cancelOrder(99L, buyer))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }
}
