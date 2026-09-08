package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.repository.EventRepository;
import com.eventmanagement.repository.TicketRepository;
import com.eventmanagement.repository.TicketTypeRepository;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServiceTest exercises purchaseTickets() against a mocked repository, one
 * call at a time, so it can't catch a regression in the pessimistic locking that
 * TicketTypeRepository.findByIdForUpdate() relies on to stop overselling. This
 * runs many purchases at the same ticket type concurrently against a real
 * (in-memory) database to prove that lock actually holds.
 *
 * The datasource is pointed at a dedicated H2 database (rather than relying on
 * @AutoConfigureTestDatabase's default). With this many threads queued on one
 * row, H2's MVStore lock manager sometimes reports plain contention as a
 * "deadlock" (SQLState 40001) even though only one row is involved - Postgres
 * would just queue the waiters. Each purchase is retried on that transient
 * error, the same way a client would need to under real SERIALIZABLE
 * contention; what this test actually asserts is that no retry count ever
 * lets more tickets sell than were available.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OrderServiceConcurrencyTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        String dbName = "order-concurrency-" + UUID.randomUUID();
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
    }

    private static final int AVAILABLE_TICKETS = 10;
    private static final int CONCURRENT_BUYERS = 30;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private Long ticketTypeId;

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
        event.setDescription("A show with very limited capacity");
        event.setLocation("Downtown Venue");
        event.setStartDate(LocalDateTime.now().plusDays(30));
        event.setEndDate(LocalDateTime.now().plusDays(30).plusHours(3));
        event.setStatus(Event.EventStatus.PUBLISHED);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        event = eventRepository.save(event);

        TicketType ticketType = TicketType.builder()
                .event(event)
                .name("General Admission")
                .price(new BigDecimal("50.00"))
                .quantity(AVAILABLE_TICKETS)
                .available(AVAILABLE_TICKETS)
                .createdAt(LocalDateTime.now())
                .build();
        ticketTypeId = ticketTypeRepository.save(ticketType).getId();
    }

    @Test
    void concurrentPurchasesNeverSellMoreTicketsThanAvailable()
            throws InterruptedException, ExecutionException, TimeoutException {
        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_BUYERS; i++) {
            buyers.add(userRepository.save(User.builder()
                    .email("buyer" + i + "@example.com")
                    .passwordHash("hash")
                    .firstName("Buyer")
                    .lastName(String.valueOf(i))
                    .role(User.Role.ATTENDEE)
                    .build()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_BUYERS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_BUYERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (User buyer : buyers) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    PurchaseRequest request = new PurchaseRequest();
                    request.setTicketTypeId(ticketTypeId);
                    request.setQuantity(1);
                    purchaseWithRetryOnContention(request, buyer);
                    successCount.incrementAndGet();
                } catch (InsufficientTicketsException e) {
                    rejectedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(AVAILABLE_TICKETS);
        assertThat(rejectedCount.get()).isEqualTo(CONCURRENT_BUYERS - AVAILABLE_TICKETS);

        TicketType updated = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(updated.getAvailable()).isZero();
        assertThat(ticketRepository.count()).isEqualTo(AVAILABLE_TICKETS);
    }

    private void purchaseWithRetryOnContention(PurchaseRequest request, User buyer) throws InterruptedException {
        int attempts = 0;
        while (true) {
            try {
                orderService.purchaseTickets(request, buyer);
                return;
            } catch (CannotAcquireLockException e) {
                attempts++;
                if (attempts >= 10) {
                    throw e;
                }
                Thread.sleep(10);
            }
        }
    }
}
