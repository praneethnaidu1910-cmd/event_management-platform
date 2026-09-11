package com.eventmanagement.repository;

import com.eventmanagement.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByOrganizerId(Long organizerId);
    List<Event> findByStatus(Event.EventStatus status);
    Page<Event> findByStatus(Event.EventStatus status, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.status = :status "
            + "AND (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:category IS NULL OR e.category = :category) "
            + "AND (:location IS NULL OR LOWER(e.location) LIKE LOWER(CONCAT('%', :location, '%'))) "
            + "AND (:startFrom IS NULL OR e.startDate >= :startFrom) "
            + "AND (:startTo IS NULL OR e.startDate <= :startTo)")
    List<Event> search(
            @Param("status") Event.EventStatus status,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("location") String location,
            @Param("startFrom") LocalDateTime startFrom,
            @Param("startTo") LocalDateTime startTo
    );
}
