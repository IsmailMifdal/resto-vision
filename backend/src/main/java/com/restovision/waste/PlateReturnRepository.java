package com.restovision.waste;

import com.restovision.domain.Restaurant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlateReturnRepository extends JpaRepository<PlateReturn, UUID> {
    @Query("select p from PlateReturn p join fetch p.zone z where z.restaurant = :restaurant "
            + "and p.occurredAt >= :from and p.occurredAt < :to order by p.occurredAt asc")
    List<PlateReturn> findBetween(@Param("restaurant") Restaurant restaurant, @Param("from") Instant from,
                                  @Param("to") Instant to);
}
