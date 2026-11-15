package com.restovision.repo;

import com.restovision.domain.AlertRule;
import com.restovision.domain.Restaurant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findByRestaurantAndEnabledTrue(Restaurant restaurant);

    List<AlertRule> findByRestaurantOrderByTypeAsc(Restaurant restaurant);
}
