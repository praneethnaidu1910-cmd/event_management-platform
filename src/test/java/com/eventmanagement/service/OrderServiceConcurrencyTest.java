package com.eventmanagement.service;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.TicketType;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.repository.EventRepository;
import com.eventmanagement.repository.TicketTypeRepository;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServiceTest exercises purchaseTickets() against a mocked repository, so it
 * never touches the PESSIMISTIC_WRITE lock that findByIdForUpdate() relies on to
 * prevent overselling. This runs the real service, real transactions, and a real
 * (in-memory) database with many buyers racing for the same limited inventory.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-concurrency-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=25"
})
class OrderServiceConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void purchaseTickets_underConcurrentDemand_neverSellsMoreThanAvailable() throws InterruptedException {
        int available = 5;
        int buyerCount = 20;

        User organizer = userRepository.save(User.builder()
                .email("organizer@example.com")
                .passwordHash("hash")
                .firstName("Org")
                .lastName("Anizer")
                .role(User.Role.ORGANIZER)
                .build());

        Event event = eventRepository.save(Event.builder()
                .organizer(organizer)
                .title("Concurrency Test Concert")
                .location("Test Arena")
                .startDate(LocalDateTime.now().plusDays(30))
                .endDate(LocalDateTime.now().plusDays(30).plusHours(3))
                .status(Event.EventStatus.PUBLISHED)
                .build());

        TicketType ticketType = ticketTypeRepository.save(TicketType.builder()
                .event(event)
                .name("General Admission")
                .price(new java.math.BigDecimal("50.00"))
                .quantity(available)
                .available(available)
                .build());

        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < buyerCount; i++) {
            buyers.add(userRepository.save(User.builder()
                    .email("buyer" + i + "@example.com")
                    .passwordHash("hash")
                    .firstName("Buyer")
                    .lastName(String.valueOf(i))
                    .role(User.Role.ATTENDEE)
                    .build()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(buyerCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(buyerCount);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficientTicketFailures = new AtomicInteger();
        List<Throwable> unexpectedFailures = new ArrayList<>();

        for (User buyer : buyers) {
            executor.submit(() -> {
                try {
                    start.await();
                    PurchaseRequest request = new PurchaseRequest();
                    request.setTicketTypeId(ticketType.getId());
                    request.setQuantity(1);
                    orderService.purchaseTickets(request, buyer);
                    successes.incrementAndGet();
                } catch (InsufficientTicketsException e) {
                    insufficientTicketFailures.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpectedFailures) {
                        unexpectedFailures.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("all purchase attempts should finish within the timeout").isTrue();
        assertThat(unexpectedFailures).as("no exception besides InsufficientTicketsException should occur").isEmpty();
        assertThat(successes.get()).isEqualTo(available);
        assertThat(insufficientTicketFailures.get()).isEqualTo(buyerCount - available);

        TicketType reloaded = ticketTypeRepository.findById(ticketType.getId()).orElseThrow();
        assertThat(reloaded.getAvailable()).isEqualTo(0);
    }
}
