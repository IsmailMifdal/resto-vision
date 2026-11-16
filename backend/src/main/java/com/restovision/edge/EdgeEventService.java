package com.restovision.edge;

import java.util.List;
import com.restovision.alert.AlertService;
import com.restovision.api.Dtos.ZoneDto;
import com.restovision.domain.Restaurant;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneEvent;
import com.restovision.domain.ZoneType;
import com.restovision.live.LiveNotifier;
import com.restovision.repo.RestaurantRepository;
import com.restovision.repo.ZoneEventRepository;
import com.restovision.repo.ZoneRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applique un événement du boîtier : état courant, historique, écrans en direct, alertes. */
@Service
public class EdgeEventService {
    private static final Logger log = LoggerFactory.getLogger(EdgeEventService.class);

    private final RestaurantRepository restaurants;
    private final ZoneRepository zones;
    private final ZoneEventRepository events;
    private final AlertService alertService;
    private final LiveNotifier notifier;

    public EdgeEventService(RestaurantRepository restaurants, ZoneRepository zones, ZoneEventRepository events,
                            AlertService alertService, LiveNotifier notifier) {
        this.restaurants = restaurants;
        this.zones = zones;
        this.events = events;
        this.alertService = alertService;
        this.notifier = notifier;
    }

    @Transactional
    public void handle(EdgeEvent event) {
        if ("LAYOUT".equals(event.type())) {
            handleLayout(event);
            return;
        }
        if (event.type() == null || event.restaurantId() == null || event.zoneId() == null) {
            log.warn("Événement incomplet ignoré : {}", event);
            return;
        }
        ZoneType type = switch (event.type()) {
            case "TABLE_STATE" -> ZoneType.TABLE;
            case "QUEUE" -> ZoneType.QUEUE;
            default -> null;
        };
        if (type == null) {
            log.debug("Type d'événement pas encore géré : {}", event.type());
            return;
        }
        Restaurant restaurant = restaurants.findByCode(event.restaurantId()).orElse(null);
        if (restaurant == null) {
            log.warn("Restaurant inconnu : {}", event.restaurantId());
            return;
        }
        Zone zone = zones.findByRestaurantAndCode(restaurant, event.zoneId())
                .orElseGet(() -> zones.save(new Zone(restaurant, event.zoneId(), type)));
        if (zone.getType() != type) {
            log.warn("La zone {} n'est pas de type {}", zone.getCode(), type);
            return;
        }

        Instant now = Instant.now();
        // Une horloge de boîtier en avance ne doit pas fausser les durées.
        Instant at = event.timestamp() == null || event.timestamp().isAfter(now) ? now : event.timestamp();
        TableState previous = zone.getState();
        if (type == ZoneType.TABLE) {
            TableState state = parseState(event.state());
            if (state == null) {
                log.warn("État de table inconnu : {}", event.state());
                return;
            }
            zone.setState(state);
            zone.setStateSince(at);
            zone.setDishes(event.dishes() == null ? 0 : event.dishes());
        } else {
            zone.setEstimatedWaitSeconds(event.estimatedWaitSeconds());
        }
        zone.setPersons(event.persons() == null ? 0 : event.persons());
        zone.setUpdatedAt(now);

        events.save(new ZoneEvent(zone, zone.getState(), previous, zone.getPersons(), zone.getDishes(), at));
        notifier.zone(restaurant.getCode(), ZoneDto.of(zone));
        alertService.evaluateZone(zone, now);
    }

    /** Le boîtier annonce le contour de ses zones : on le garde pour dessiner le plan de salle. */
    private void handleLayout(EdgeEvent event) {
        Restaurant restaurant = event.restaurantId() == null ? null
                : restaurants.findByCode(event.restaurantId()).orElse(null);
        if (restaurant == null || event.zones() == null) {
            log.warn("Plan ignoré, restaurant inconnu ou plan vide : {}", event.restaurantId());
            return;
        }
        for (EdgeEvent.ZoneLayout layout : event.zones()) {
            ZoneType type = "TABLE".equals(layout.type()) ? ZoneType.TABLE
                    : "QUEUE".equals(layout.type()) ? ZoneType.QUEUE : null;
            List<List<Double>> polygon = layout.polygon();
            boolean valid = layout.id() != null && type != null && polygon != null
                    && polygon.size() >= 3 && polygon.size() <= 100
                    && polygon.stream().allMatch(point -> point != null && point.size() == 2
                            && point.get(0) != null && point.get(1) != null);
            if (!valid) {
                log.warn("Zone invalide ignorée dans le plan de {} : {}", restaurant.getCode(), layout.id());
                continue;
            }
            Zone zone = zones.findByRestaurantAndCode(restaurant, layout.id())
                    .orElseGet(() -> zones.save(new Zone(restaurant, layout.id(), type)));
            if (zone.getType() != type) {
                log.warn("La zone {} n'est pas de type {}", zone.getCode(), type);
                continue;
            }
            if (!polygon.equals(zone.getPolygon())) {
                zone.setPolygon(polygon);
                notifier.zone(restaurant.getCode(), ZoneDto.of(zone));
            }
        }
    }

    private static TableState parseState(String value) {
        try {
            return value == null ? null : TableState.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
