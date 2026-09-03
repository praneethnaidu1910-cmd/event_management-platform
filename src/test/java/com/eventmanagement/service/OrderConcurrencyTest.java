package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.repository.EventRepository;
import com.eventmanagement.repository.OrderRepository;
import com.eventmanagement.repository.TicketTypeRepository;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderService mocked-repository tests (OrderServiceTest) never exercise the
 * pessimistic lock that findByIdForUpdate takes, so they can't catch an
 * oversell under real concurrent access. This runs many buyers at the ticket
 * type at once against a real (in-memory) database and checks that exactly
 * as many tickets are sold as were available.
 *
 * The class-level NOT_SUPPORTED propagation disables the transaction that
 * @DataJpaTest normally wraps each test in, so each purchaseTickets() call
 * runs (and commits) in its own transaction, the same as in production.
 */
@DataJpaTest
@Import(OrderService.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long ticketTypeId;
    private List<User> buyers;

    private static final int AVAILABLE_TICKETS = 5;
    private static final int BUYER_COUNT = 20;

    @BeforeEach
    void setUp() {
        User organizer = userRepository.save(User.builder()
                .email("organizer@example.com")
                .passwordHash("hash")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(User.Role.ORGANIZER)
                .build());

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle("Sold Out Show");
        event.setDescription("Only a few seats left");
        event.setLocation("Small Venue");
        event.setStartDate(LocalDateTime.of(2026, 12, 1, 20, 0));
        event.setEndDate(LocalDateTime.of(2026, 12, 1, 23, 0));
        event.setStatus(Event.EventStatus.PUBLISHED);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        event = eventRepository.save(event);

        TicketType ticketType = TicketType.builder()
                .event(event)
                .name("General Admission")
                .price(new BigDecimal("40.00"))
                .quantity(AVAILABLE_TICKETS)
                .available(AVAILABLE_TICKETS)
                .createdAt(LocalDateTime.now())
                .build();
        ticketTypeId = ticketTypeRepository.save(ticketType).getId();

        buyers = IntStream.range(0, BUYER_COUNT)
                .mapToObj(i -> userRepository.save(User.builder()
                        .email("buyer" + i + "@example.com")
                        .passwordHash("hash")
                        .firstName("Buyer")
                        .lastName(String.valueOf(i))
                        .role(User.Role.ATTENDEE)
                        .build()))
                .collect(Collectors.toList());
    }

    @Test
    void concurrentPurchasesNeverSellMoreTicketsThanAreAvailable() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(BUYER_COUNT);
        CountDownLatch ready = new CountDownLatch(BUYER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientCount = new AtomicInteger();

        for (User buyer : buyers) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    PurchaseRequest request = new PurchaseRequest();
                    request.setTicketTypeId(ticketTypeId);
                    request.setQuantity(1);
                    orderService.purchaseTickets(request, buyer);
                    successCount.incrementAndGet();
                } catch (InsufficientTicketsException e) {
                    insufficientCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(AVAILABLE_TICKETS);
        assertThat(insufficientCount.get()).isEqualTo(BUYER_COUNT - AVAILABLE_TICKETS);
        assertThat(successCount.get() + insufficientCount.get()).isEqualTo(BUYER_COUNT);

        TicketType persisted = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(persisted.getAvailable()).isZero();
        assertThat(orderRepository.count()).isEqualTo(AVAILABLE_TICKETS);
    }
}
