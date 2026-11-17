package com.restovision.repo;

import com.restovision.domain.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    Optional<Restaurant> findByCode(String code);

    List<Restaurant> findByGroupCodeOrderByNameAsc(String groupCode);
}
