package com.restovision.edge;

import com.restovision.domain.Restaurant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeBoxRepository extends JpaRepository<EdgeBox, UUID> {
    Optional<EdgeBox> findByRestaurantAndBoxId(Restaurant restaurant, String boxId);
    List<EdgeBox> findByRestaurantOrderByBoxIdAsc(Restaurant restaurant);
    List<EdgeBox> findByOnlineTrueAndLastSeenBefore(Instant before);
}
