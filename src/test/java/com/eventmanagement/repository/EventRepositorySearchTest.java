package com.eventmanagement.repository;

import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search query lives in a @Query annotation, so the mocked-repository
 * unit tests in EventServiceTest never actually execute the JPQL - a typo
 * there would only surface at runtime. This runs it against a real
 * (in-memory) database instead.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EventRepositorySearchTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private User organizer;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(User.builder()
                .email("organizer@example.com")
                .passwordHash("hash")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(User.Role.ORGANIZER)
                .build());

        eventRepository.save(event("Jazz Night", "Live jazz downtown", "Music",
                "Blue Note, NYC", LocalDateTime.of(2026, 9, 10, 20, 0), Event.EventStatus.PUBLISHED));
        eventRepository.save(event("Tech Conference", "Annual developer conference", "Tech",
                "Convention Center, SF", LocalDateTime.of(2026, 10, 5, 9, 0), Event.EventStatus.PUBLISHED));
        eventRepository.save(event("Unlisted Meetup", "Not published yet", "Tech",
                "Somewhere", LocalDateTime.of(2026, 10, 6, 9, 0), Event.EventStatus.DRAFT));
    }

    private Event event(String title, String description, String category, String location,
                         LocalDateTime startDate, Event.EventStatus status) {
        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle(title);
        event.setDescription(description);
        event.setCategory(category);
        event.setLocation(location);
        event.setStartDate(startDate);
        event.setEndDate(startDate.plusHours(3));
        event.setStatus(status);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }

    @Test
    void searchByKeywordMatchesTitleOrDescriptionCaseInsensitively() {
        List<Event> results = eventRepository.search(Event.EventStatus.PUBLISHED, "jazz", null, null, null, null);

        assertThat(results).extracting(Event::getTitle).containsExactly("Jazz Night");
    }

    @Test
    void searchExcludesUnpublishedEventsEvenWhenTheyMatchOtherFilters() {
        List<Event> results = eventRepository.search(Event.EventStatus.PUBLISHED, null, "Tech", null, null, null);

        assertThat(results).extracting(Event::getTitle).containsExactly("Tech Conference");
    }

    @Test
    void searchByStartDateRangeFiltersCorrectly() {
        List<Event> results = eventRepository.search(
                Event.EventStatus.PUBLISHED, null, null, null,
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 31, 23, 59));

        assertThat(results).extracting(Event::getTitle).containsExactly("Tech Conference");
    }

    @Test
    void searchPaginated_sortsByStartDateAndReportsTotals() {
        Pageable firstPage = PageRequest.of(0, 1, Sort.by("startDate").ascending());
        Page<Event> page = eventRepository.search(
                Event.EventStatus.PUBLISHED, null, null, null, null, null, firstPage);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Event::getTitle).containsExactly("Jazz Night");
    }
}
