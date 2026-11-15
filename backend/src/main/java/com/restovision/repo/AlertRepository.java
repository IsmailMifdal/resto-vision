package com.restovision.repo;

import com.restovision.domain.Alert;
import com.restovision.domain.AlertType;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Zone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByZoneAndTypeAndResolvedAtIsNull(Zone zone, AlertType type);

    @Query("select a from Alert a join fetch a.zone z "
            + "where z.restaurant = :restaurant and a.resolvedAt is null order by a.createdAt desc")
    List<Alert> findOpen(@Param("restaurant") Restaurant restaurant);
}
