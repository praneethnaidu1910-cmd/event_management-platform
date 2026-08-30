package com.eventmanagement.service;

import com.eventmanagement.dto.response.AdminOrderResponse;
import com.eventmanagement.dto.response.PlatformStatsResponse;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.Order;
import com.eventmanagement.entity.Ticket;
import com.eventmanagement.entity.User;
import com.eventmanagement.repository.EventRepository;
import com.eventmanagement.repository.OrderRepository;
import com.eventmanagement.repository.TicketRepository;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private EventRepository eventRepository;
    private TicketRepository ticketRepository;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        eventRepository = mock(EventRepository.class);
        ticketRepository = mock(TicketRepository.class);
        adminService = new AdminService(orderRepository, userRepository, eventRepository, ticketRepository);
    }

    @Test
    void listAllOrders_mapsEachOrderToAnAdminOrderResponse() {
        User buyer = User.builder().id(5L).email("buyer@example.com").role(User.Role.ATTENDEE).build();
        Event event = Event.builder().id(10L).title("Tech Conference").build();
        Order order = Order.builder()
                .id(7L)
                .user(buyer)
                .event(event)
                .totalAmount(new BigDecimal("75.00"))
                .paymentStatus("COMPLETED")
                .createdAt(LocalDateTime.of(2026, 8, 30, 12, 0))
                .tickets(List.of(
                        Ticket.builder().id(100L).status("ACTIVE").build(),
                        Ticket.builder().id(101L).status("ACTIVE").build()))
                .build();

        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<AdminOrderResponse> responses = adminService.listAllOrders();

        assertThat(responses).hasSize(1);
        AdminOrderResponse response = responses.get(0);
        assertThat(response.getOrderId()).isEqualTo(7L);
        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getUserEmail()).isEqualTo("buyer@example.com");
        assertThat(response.getEventId()).isEqualTo(10L);
        assertThat(response.getEventTitle()).isEqualTo("Tech Conference");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("75.00");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTicketCount()).isEqualTo(2);
    }

    @Test
    void getPlatformStats_aggregatesCountsAndRevenueFromEachRepository() {
        when(userRepository.count()).thenReturn(42L);
        when(eventRepository.count()).thenReturn(15L);
        when(eventRepository.countByStatus(Event.EventStatus.PUBLISHED)).thenReturn(9L);
        when(orderRepository.count()).thenReturn(30L);
        when(ticketRepository.countByStatus("ACTIVE")).thenReturn(55L);
        when(orderRepository.sumRevenue()).thenReturn(new BigDecimal("1234.56"));

        PlatformStatsResponse stats = adminService.getPlatformStats();

        assertThat(stats.getTotalUsers()).isEqualTo(42L);
        assertThat(stats.getTotalEvents()).isEqualTo(15L);
        assertThat(stats.getPublishedEvents()).isEqualTo(9L);
        assertThat(stats.getTotalOrders()).isEqualTo(30L);
        assertThat(stats.getActiveTicketsSold()).isEqualTo(55L);
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("1234.56");
    }
}
