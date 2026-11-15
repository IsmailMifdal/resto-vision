package com.restovision.stats;

import com.restovision.api.Dtos.StatsDto;
import com.restovision.domain.Restaurant;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneEvent;
import com.restovision.domain.ZoneType;
import com.restovision.repo.ZoneEventRepository;
import com.restovision.repo.ZoneRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Indicateurs du jour pour le dashboard du gérant. */
@Service
public class StatsService {
    private final ZoneRepository zones;
    private final ZoneEventRepository events;
    private final ZoneId timezone;

    public StatsService(ZoneRepository zones, ZoneEventRepository events,
                        @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.zones = zones;
        this.events = events;
        this.timezone = ZoneId.of(timezone);
    }

    public StatsDto today(Restaurant restaurant) {
        Instant startOfDay = LocalDate.now(timezone).atStartOfDay(timezone).toInstant();
        return compute(zones.findByRestaurantOrderByCodeAsc(restaurant), events.findSince(restaurant, startOfDay));
    }

    static StatsDto compute(List<Zone> zoneList, List<ZoneEvent> history) {
        int tables = 0, occupied = 0, toClear = 0, queuePersons = 0, queueWait = 0;
        for (Zone zone : zoneList) {
            if (zone.getType() == ZoneType.TABLE) {
                tables++;
                if (zone.getState() == TableState.WAITING || zone.getState() == TableState.SERVED) {
                    occupied++;
                } else if (zone.getState() == TableState.TO_CLEAR) {
                    toClear++;
                }
            } else {
                queuePersons += zone.getPersons();
                queueWait = Math.max(queueWait, zone.getEstimatedWaitSeconds() == null ? 0 : zone.getEstimatedWaitSeconds());
            }
        }

        Map<UUID, Instant> waitingSince = new HashMap<>();
        long totalWaitSeconds = 0;
        int servedCount = 0, seatings = 0;
        for (ZoneEvent event : history) {
            if (event.getZone().getType() != ZoneType.TABLE) {
                continue;
            }
            UUID zoneId = event.getZone().getId();
            boolean wasFree = event.getPreviousState() == TableState.EMPTY || event.getPreviousState() == TableState.TO_CLEAR;
            if (event.getState() == TableState.WAITING) {
                waitingSince.put(zoneId, event.getOccurredAt());
                seatings++;
            } else if (event.getState() == TableState.SERVED) {
                Instant since = waitingSince.remove(zoneId);
                if (since != null) {
                    totalWaitSeconds += Duration.between(since, event.getOccurredAt()).getSeconds();
                    servedCount++;
                } else if (wasFree) {
                    seatings++;
                }
            } else {
                waitingSince.remove(zoneId);
            }
        }
        Integer averageWait = servedCount == 0 ? null : (int) (totalWaitSeconds / servedCount);
        double occupancy = tables == 0 ? 0 : (double) occupied / tables;
        return new StatsDto(tables, occupied, toClear, occupancy, averageWait, seatings, queuePersons, queueWait);
    }
}
