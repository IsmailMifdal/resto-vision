package com.restovision.repo;

import com.restovision.domain.Restaurant;
import com.restovision.domain.ZoneEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventRepository extends JpaRepository<ZoneEvent, UUID> {
    @Query("select e from ZoneEvent e join fetch e.zone z "
            + "where z.restaurant = :restaurant and e.occurredAt >= :since order by e.occurredAt asc")
    List<ZoneEvent> findSince(@Param("restaurant") Restaurant restaurant, @Param("since") Instant since);

    @Query("select e from ZoneEvent e join fetch e.zone z "
            + "where z.restaurant = :restaurant and e.occurredAt >= :from and e.occurredAt < :to "
            + "order by e.occurredAt asc")
    List<ZoneEvent> findBetween(@Param("restaurant") Restaurant restaurant, @Param("from") Instant from,
                                @Param("to") Instant to);

    @Query("select count(e) from ZoneEvent e where e.zone.restaurant = :restaurant and e.occurredAt < :before")
    long countBefore(@Param("restaurant") Restaurant restaurant, @Param("before") Instant before);
}
