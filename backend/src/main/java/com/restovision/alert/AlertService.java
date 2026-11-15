package com.restovision.alert;

import com.restovision.api.Dtos.AlertDto;
import com.restovision.domain.Alert;
import com.restovision.domain.AlertRule;
import com.restovision.domain.AlertType;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneType;
import com.restovision.live.LiveNotifier;
import com.restovision.repo.AlertRepository;
import com.restovision.repo.AlertRuleRepository;
import com.restovision.repo.ZoneRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ouvre une alerte quand une règle est vraie, la ferme quand elle ne l'est plus. */
@Service
public class AlertService {
    private final AlertRuleRepository rules;
    private final AlertRepository alerts;
    private final ZoneRepository zones;
    private final LiveNotifier notifier;

    public AlertService(AlertRuleRepository rules, AlertRepository alerts, ZoneRepository zones, LiveNotifier notifier) {
        this.rules = rules;
        this.alerts = alerts;
        this.zones = zones;
        this.notifier = notifier;
    }

    public static boolean isTriggered(AlertType type, int threshold, Zone zone, Instant now) {
        return switch (type) {
            case UNSERVED -> inStateFor(zone, TableState.WAITING, threshold, now);
            case TO_CLEAR -> inStateFor(zone, TableState.TO_CLEAR, threshold, now);
            case QUEUE_LENGTH -> zone.getType() == ZoneType.QUEUE && zone.getPersons() >= threshold;
        };
    }

    private static boolean inStateFor(Zone zone, TableState state, int seconds, Instant now) {
        return zone.getType() == ZoneType.TABLE
                && zone.getState() == state
                && zone.getStateSince() != null
                && Duration.between(zone.getStateSince(), now).getSeconds() >= seconds;
    }

    static String message(AlertType type, Zone zone, Instant now) {
        return switch (type) {
            case UNSERVED -> "Table " + zone.getCode() + " : clients non servis depuis " + minutes(zone, now) + " min";
            case TO_CLEAR -> "Table " + zone.getCode() + " : à débarrasser depuis " + minutes(zone, now) + " min";
            case QUEUE_LENGTH -> "File " + zone.getCode() + " : " + zone.getPersons() + " personnes en attente";
        };
    }

    private static long minutes(Zone zone, Instant now) {
        return Math.max(1, Duration.between(zone.getStateSince(), now).toMinutes());
    }

    /** Vérification périodique : le temps passe même quand le boîtier n'envoie rien. */
    @Scheduled(fixedDelayString = "${restovision.alerts.check-interval-ms:5000}")
    @Transactional
    public void evaluateAll() {
        evaluateAll(Instant.now());
    }

    @Transactional
    public void evaluateAll(Instant now) {
        for (AlertRule rule : rules.findByEnabledTrue()) {
            for (Zone zone : zones.findByRestaurantOrderByCodeAsc(rule.getRestaurant())) {
                apply(rule, zone, now);
            }
        }
    }

    /** Vérification immédiate après un événement du boîtier. */
    @Transactional
    public void evaluateZone(Zone zone, Instant now) {
        for (AlertRule rule : rules.findByRestaurantAndEnabledTrue(zone.getRestaurant())) {
            apply(rule, zone, now);
        }
    }

    private void apply(AlertRule rule, Zone zone, Instant now) {
        boolean triggered = isTriggered(rule.getType(), rule.getThreshold(), zone, now);
        List<Alert> open = alerts.findByZoneAndTypeAndResolvedAtIsNull(zone, rule.getType());
        String restaurantCode = zone.getRestaurant().getCode();
        if (triggered && open.isEmpty()) {
            Alert alert = alerts.save(new Alert(zone, rule.getType(), message(rule.getType(), zone, now), now));
            notifier.alert(restaurantCode, AlertDto.of(alert));
        } else if (!triggered) {
            for (Alert alert : open) {
                alert.setResolvedAt(now);
                notifier.alert(restaurantCode, AlertDto.of(alert));
            }
        }
    }
}
