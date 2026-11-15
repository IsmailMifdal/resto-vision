package com.restovision.demo;

import com.restovision.domain.AlertRule;
import com.restovision.domain.AlertType;
import com.restovision.domain.AppUser;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Role;
import com.restovision.repo.AlertRuleRepository;
import com.restovision.repo.AppUserRepository;
import com.restovision.repo.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Données de démonstration : restaurant "demo" et compte demo@restovision.local / demo1234.
 * Les seuils sont volontairement courts pour voir des alertes avec le simulateur.
 * À désactiver en production (DEMO_DATA=false).
 */
@Component
@ConditionalOnProperty(name = "restovision.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final RestaurantRepository restaurants;
    private final AlertRuleRepository rules;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(RestaurantRepository restaurants, AlertRuleRepository rules, AppUserRepository users,
                          PasswordEncoder passwordEncoder) {
        this.restaurants = restaurants;
        this.rules = rules;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (restaurants.findByCode("demo").isPresent()) {
            return;
        }
        Restaurant demo = restaurants.save(new Restaurant("demo", "Restaurant de démonstration"));
        rules.save(new AlertRule(demo, AlertType.UNSERVED, 30));
        rules.save(new AlertRule(demo, AlertType.TO_CLEAR, 20));
        rules.save(new AlertRule(demo, AlertType.QUEUE_LENGTH, 4));
        users.save(new AppUser("demo@restovision.local", passwordEncoder.encode("demo1234"), Role.MANAGER, demo));
        log.info("Données de démonstration créées : demo@restovision.local / demo1234");
    }
}
