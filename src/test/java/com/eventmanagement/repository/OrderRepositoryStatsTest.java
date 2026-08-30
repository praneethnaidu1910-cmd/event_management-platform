package com.eventmanagement.repository;

import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.Order;
import com.eventmanagement.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sumRevenue() lives in a @Query annotation, so a mocked-repository unit
 * test would never actually execute the JPQL - a typo there would only
 * surface at runtime. This runs it against a real (in-memory) database
 * instead, the same way EventRepositorySearchTest covers Event's search
 * query.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OrderRepositoryStatsTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    private User buyer;
    private Event event;

    @BeforeEach
    void setUp() {
        User organizer = userRepository.save(User.builder()
                .email("organizer@example.com")
                .passwordHash("hash")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(User.Role.ORGANIZER)
                .build());

        buyer = userRepository.save(User.builder()
                .email("buyer@example.com")
                .passwordHash("hash")
                .firstName("Grace")
                .lastName("Hopper")
                .role(User.Role.ATTENDEE)
                .build());

        event = eventRepository.save(Event.builder()
                .organizer(organizer)
                .title("Tech Conference")
                .location("Austin")
                .startDate(LocalDateTime.now().plusDays(10))
                .endDate(LocalDateTime.now().plusDays(11))
                .status(Event.EventStatus.PUBLISHED)
                .build());
    }

    private void saveOrder(String paymentStatus, String amount) {
        orderRepository.save(Order.builder()
                .user(buyer)
                .event(event)
                .totalAmount(new BigDecimal(amount))
                .paymentStatus(paymentStatus)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void sumRevenue_excludesCancelledOrders() {
        saveOrder("COMPLETED", "50.00");
        saveOrder("COMPLETED", "25.50");
        saveOrder("CANCELLED", "100.00");

        BigDecimal revenue = orderRepository.sumRevenue();

        assertThat(revenue).isEqualByComparingTo("75.50");
    }

    @Test
    void sumRevenue_returnsZeroWhenThereAreNoOrders() {
        BigDecimal revenue = orderRepository.sumRevenue();

        assertThat(revenue).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
