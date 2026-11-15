package com.restovision.repo;

import com.restovision.domain.Restaurant;
import com.restovision.domain.Zone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    Optional<Zone> findByRestaurantAndCode(Restaurant restaurant, String code);

    List<Zone> findByRestaurantOrderByCodeAsc(Restaurant restaurant);
}
