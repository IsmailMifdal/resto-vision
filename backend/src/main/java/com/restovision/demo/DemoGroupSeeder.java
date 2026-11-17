package com.restovision.demo;

import com.restovision.domain.AppUser;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Role;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneEvent;
import com.restovision.domain.ZoneType;
import com.restovision.repo.AppUserRepository;
import com.restovision.repo.RestaurantRepository;
import com.restovision.repo.ZoneEventRepository;
import com.restovision.repo.ZoneRepository;
import com.restovision.waste.PlateReturnRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groupe de démonstration : le restaurant « demo » et deux autres sites, avec une semaine d'historique chacun,
 * et le compte groupe@restovision.local / demo1234 (responsable de groupe) pour l'écran Groupe.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "restovision.demo.enabled", havingValue = "true")
public class DemoGroupSeeder implements CommandLineRunner {
    static final String GROUP = "demo-groupe";
    private static final Logger log = LoggerFactory.getLogger(DemoGroupSeeder.class);

    /** Code, nom, nombre de tables, secondes d'attente en plus : chaque site a son caractère. */
    private record Site(String code, String name, int tables, int slowerBy) {}

    private static final List<Site> SITES = List.of(
            new Site("demo-bastille", "Démo Bastille", 10, -90),
            new Site("demo-gare", "Démo Gare", 6, 240));

    private final RestaurantRepository restaurants;
    private final ZoneRepository zones;
    private final ZoneEventRepository events;
    private final PlateReturnRepository plateReturns;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean history;
    private final ZoneId timezone;

    public DemoGroupSeeder(RestaurantRepository restaurants, ZoneRepository zones, ZoneEventRepository events,
                           PlateReturnRepository plateReturns, AppUserRepository users, PasswordEncoder passwordEncoder,
                           @Value("${restovision.demo.history:false}") boolean history,
                           @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.restaurants = restaurants;
        this.zones = zones;
        this.events = events;
        this.plateReturns = plateReturns;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.history = history;
        this.timezone = ZoneId.of(timezone);
    }

    @Override
    @Transactional
    public void run(String... args) {
        Restaurant demo = restaurants.findByCode("demo").orElse(null);
        if (demo == null || restaurants.findByCode(SITES.get(0).code()).isPresent()) {
            return;
        }
        demo.setGroupCode(GROUP);
        users.save(new AppUser("groupe@restovision.local", passwordEncoder.encode("demo1234"), Role.ADMIN, demo));
        LocalDate today = LocalDate.now(timezone);
        Instant now = Instant.now();
        for (Site site : SITES) {
            Restaurant restaurant = new Restaurant(site.code(), site.name());
            restaurant.setGroupCode(GROUP);
            restaurants.save(restaurant);
            if (!history) {
                continue;
            }
            List<ZoneEvent> created = new ArrayList<>();
            for (int number = 1; number <= site.tables(); number++) {
                Zone table = zones.save(new Zone(restaurant, "T" + number, ZoneType.TABLE));
                for (int back = DemoDataSeeder.HISTORY_DAYS; back >= 1; back--) {
                    // Graine décalée : chaque site vit ses propres services.
                    created.addAll(DemoDataSeeder.serviceDay(table, number + 100 * (SITES.indexOf(site) + 1),
                            today.minusDays(back), timezone, now, site.slowerBy()));
                }
            }
            events.saveAll(created);
            plateReturns.saveAll(DemoDataSeeder.plateReturns(created));
        }
        log.info("Groupe de démonstration créé : {} sites, compte groupe@restovision.local / demo1234", SITES.size() + 1);
    }
}
