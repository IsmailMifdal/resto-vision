package com.restovision.edge;

import com.restovision.api.Dtos.BoxDto;
import com.restovision.domain.Restaurant;
import com.restovision.live.LiveNotifier;
import com.restovision.repo.RestaurantRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Supervision des boîtiers. Un boîtier envoie un signal de vie toutes les 30 secondes ; sans nouvelles
 * pendant `offline-after-seconds`, il est déclaré hors ligne et les écrans le signalent : l'état des
 * tables affiché n'est alors plus à jour.
 */
@Service
public class BoxService {
    private static final Logger log = LoggerFactory.getLogger(BoxService.class);

    private final RestaurantRepository restaurants;
    private final EdgeBoxRepository boxes;
    private final LiveNotifier notifier;
    private final long offlineAfterSeconds;

    public BoxService(RestaurantRepository restaurants, EdgeBoxRepository boxes, LiveNotifier notifier,
                      @Value("${restovision.boxes.offline-after-seconds:90}") long offlineAfterSeconds) {
        this.restaurants = restaurants;
        this.boxes = boxes;
        this.notifier = notifier;
        this.offlineAfterSeconds = offlineAfterSeconds;
    }

    @Transactional
    public void heartbeat(EdgeEvent event) {
        if (event.restaurantId() == null || event.boxId() == null || event.boxId().isBlank()) {
            log.warn("Signal de vie incomplet ignoré : {}", event);
            return;
        }
        Restaurant restaurant = restaurants.findByCode(event.restaurantId()).orElse(null);
        if (restaurant == null) {
            log.warn("Signal de vie d'un restaurant inconnu : {}", event.restaurantId());
            return;
        }
        String boxId = event.boxId().strip();
        EdgeBox box = boxes.findByRestaurantAndBoxId(restaurant, boxId.length() > 120 ? boxId.substring(0, 120) : boxId)
                .orElseGet(() -> new EdgeBox(restaurant, boxId.length() > 120 ? boxId.substring(0, 120) : boxId));
        if (!box.isOnline() && box.getLastSeen() != null) {
            log.info("Boîtier {} de {} de nouveau en ligne", box.getBoxId(), restaurant.getCode());
        }
        // L'heure du serveur fait foi : l'horloge d'un boîtier peut dériver.
        box.heard(event.source(), event.version(), event.fps(), event.buffered() == null ? 0 : event.buffered(),
                event.uptimeSeconds() == null ? 0 : event.uptimeSeconds(), Instant.now());
        boxes.save(box);
        notifier.box(restaurant.getCode(), BoxDto.of(box));
    }

    /** Déclare hors ligne les boîtiers silencieux. */
    @Scheduled(fixedDelayString = "${restovision.boxes.check-interval-ms:15000}")
    @Transactional
    public void checkSilentBoxes() {
        checkSilentBoxes(Instant.now());
    }

    void checkSilentBoxes(Instant now) {
        for (EdgeBox box : boxes.findByOnlineTrueAndLastSeenBefore(now.minusSeconds(offlineAfterSeconds))) {
            box.markOffline();
            log.warn("Boîtier {} de {} hors ligne : aucun signal depuis {}", box.getBoxId(),
                    box.getRestaurant().getCode(), box.getLastSeen());
            notifier.box(box.getRestaurant().getCode(), BoxDto.of(box));
        }
    }

    @Transactional(readOnly = true)
    public List<BoxDto> list(String restaurantCode) {
        Restaurant restaurant = restaurants.findByCode(restaurantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        return boxes.findByRestaurantOrderByBoxIdAsc(restaurant).stream().map(BoxDto::of).toList();
    }
}
