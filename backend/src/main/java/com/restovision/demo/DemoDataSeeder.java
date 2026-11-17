package com.restovision.demo;

import com.restovision.waste.PlateReturn;
import com.restovision.waste.PlateReturnRepository;
import com.restovision.domain.AlertRule;
import com.restovision.domain.AlertType;
import com.restovision.domain.AppUser;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Role;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneEvent;
import com.restovision.domain.ZoneType;
import com.restovision.repo.AlertRuleRepository;
import com.restovision.repo.AppUserRepository;
import com.restovision.repo.RestaurantRepository;
import com.restovision.repo.ZoneEventRepository;
import com.restovision.repo.ZoneRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
 * Données de démonstration : restaurant "demo" et compte demo@restovision.local / demo1234.
 * Les seuils sont volontairement courts pour voir des alertes avec le simulateur.
 * Avec DEMO_HISTORY=true, une semaine de services fictifs est ajoutée pour que l'écran du gérant
 * ait quelque chose à montrer. À désactiver en production (DEMO_DATA=false).
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "restovision.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final int HISTORY_DAYS = 7;
    static final int TABLES = 8;

    private final RestaurantRepository restaurants;
    private final AlertRuleRepository rules;
    private final AppUserRepository users;
    private final ZoneRepository zones;
    private final ZoneEventRepository events;
    private final PasswordEncoder passwordEncoder;
    private final PlateReturnRepository plateReturns;
    private final boolean history;
    private final ZoneId timezone;

    public DemoDataSeeder(RestaurantRepository restaurants, AlertRuleRepository rules, AppUserRepository users,
                          ZoneRepository zones, ZoneEventRepository events, PasswordEncoder passwordEncoder,
                          PlateReturnRepository plateReturns,
                          @Value("${restovision.demo.history:false}") boolean history,
                          @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.restaurants = restaurants;
        this.rules = rules;
        this.users = users;
        this.zones = zones;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.plateReturns = plateReturns;
        this.history = history;
        this.timezone = ZoneId.of(timezone);
    }

    @Override
    @Transactional
    public void run(String... args) {
        Restaurant demo = restaurants.findByCode("demo").orElse(null);
        if (demo == null) {
            demo = restaurants.save(new Restaurant("demo", "Restaurant de démonstration"));
            rules.save(new AlertRule(demo, AlertType.UNSERVED, 30));
            rules.save(new AlertRule(demo, AlertType.TO_CLEAR, 20));
            rules.save(new AlertRule(demo, AlertType.QUEUE_LENGTH, 4));
            users.save(new AppUser("demo@restovision.local", passwordEncoder.encode("demo1234"), Role.MANAGER, demo));
            log.info("Données de démonstration créées : demo@restovision.local / demo1234");
        }
        Instant startOfToday = LocalDate.now(timezone).atStartOfDay(timezone).toInstant();
        if (history && events.countBefore(demo, startOfToday) == 0) {
            seedHistory(demo, Instant.now());
        }
    }

    private void seedHistory(Restaurant demo, Instant now) {
        List<ZoneEvent> created = new ArrayList<>();
        LocalDate today = LocalDate.now(timezone);
        for (int number = 1; number <= TABLES; number++) {
            String code = "T" + number;
            Restaurant restaurant = demo;
            Zone table = zones.findByRestaurantAndCode(demo, code)
                    .orElseGet(() -> zones.save(new Zone(restaurant, code, ZoneType.TABLE)));
            for (int back = HISTORY_DAYS; back >= 0; back--) {
                created.addAll(serviceDay(table, number, today.minusDays(back), timezone, now));
            }
        }
        events.saveAll(created);
        plateReturns.saveAll(plateReturns(created));
        log.info("Historique de démonstration créé : {} événements sur {} jours", created.size(), HISTORY_DAYS + 1);
    }

    /** À chaque départ de clients : deux à quatre plats servis, et environ un repas sur cinq qui laisse un plat. */
    static List<PlateReturn> plateReturns(List<ZoneEvent> history) {
        List<PlateReturn> out = new ArrayList<>();
        for (ZoneEvent event : history) {
            if (event.getState() == TableState.TO_CLEAR) {
                Random random = new Random(event.getOccurredAt().toEpochMilli());
                int served = Math.max(1, event.getDishes());
                out.add(new PlateReturn(event.getZone(), event.getOccurredAt(), served, random.nextInt(5) == 0 ? 1 : 0));
            }
        }
        return out;
    }

    /** Une journée fictive pour une table : déjeuner et dîner, toujours la même pour une date donnée. */
    static List<ZoneEvent> serviceDay(Zone table, int tableNumber, LocalDate date, ZoneId timezone, Instant now) {
        return serviceDay(table, tableNumber, date, timezone, now, 0);
    }

    /** `slowerBy` : secondes d'attente en plus à chaque service, pour donner à un site de démo son caractère. */
    static List<ZoneEvent> serviceDay(Zone table, int tableNumber, LocalDate date, ZoneId timezone, Instant now,
                                      int slowerBy) {
        Random random = new Random(date.toEpochDay() * 31 + tableNumber);
        boolean busyEvening = date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY;
        List<ZoneEvent> out = new ArrayList<>();
        addService(out, table, date, LocalTime.of(12, 0), LocalTime.of(14, 30), 35, 20, false, random, timezone, now, slowerBy);
        addService(out, table, date, LocalTime.of(19, 0), LocalTime.of(22, 30), 50, 30, busyEvening, random, timezone, now,
                slowerBy);
        return out;
    }

    private static void addService(List<ZoneEvent> out, Zone table, LocalDate date, LocalTime opens, LocalTime closes,
                                   int mealMinutes, int mealSpread, boolean busy, Random random, ZoneId timezone,
                                   Instant now, int slowerBy) {
        Instant close = date.atTime(closes).atZone(timezone).toInstant();
        Instant seated = date.atTime(opens).atZone(timezone).toInstant()
                .plus(Duration.ofMinutes(random.nextInt(busy ? 12 : 30)));
        while (seated.plus(Duration.ofMinutes(40)).isBefore(close)) {
            // La plupart des tables sont servies en 3 à 9 minutes ; une sur dix attend bien plus.
            int waitSeconds = random.nextInt(10) == 0 ? 720 + random.nextInt(300) : 180 + random.nextInt(360);
            if (busy) {
                waitSeconds += 120;
            }
            waitSeconds += slowerBy;
            int clearSeconds = random.nextInt(8) == 0 ? 540 + random.nextInt(300) : 120 + random.nextInt(300);
            Instant served = seated.plusSeconds(waitSeconds);
            Instant left = served.plus(Duration.ofMinutes(mealMinutes + random.nextInt(mealSpread)));
            Instant cleared = left.plusSeconds(clearSeconds);
            if (cleared.isAfter(now)) {
                return;  // on n'invente rien dans le futur ni sur le service en cours
            }
            int persons = 2 + random.nextInt(3);
            out.add(new ZoneEvent(table, TableState.WAITING, TableState.EMPTY, persons, 0, seated));
            out.add(new ZoneEvent(table, TableState.SERVED, TableState.WAITING, persons, persons, served));
            out.add(new ZoneEvent(table, TableState.TO_CLEAR, TableState.SERVED, 0, persons, left));
            out.add(new ZoneEvent(table, TableState.EMPTY, TableState.TO_CLEAR, 0, 0, cleared));
            seated = cleared.plus(Duration.ofMinutes((busy ? 3 : 6) + random.nextInt(busy ? 10 : 25)));
        }
    }
}
