package com.restovision.stats;

import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.DaySummaryDto;
import com.restovision.api.Dtos.HourDto;
import com.restovision.api.Dtos.SegmentDto;
import com.restovision.api.Dtos.TableTimelineDto;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historique d'un service : ce que chaque table a vécu dans la journée, et les indicateurs qui en découlent. */
@Service
public class HistoryService {
    private final ZoneRepository zones;
    private final ZoneEventRepository events;
    private final ZoneId timezone;

    public HistoryService(ZoneRepository zones, ZoneEventRepository events,
                          @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.zones = zones;
        this.events = events;
        this.timezone = ZoneId.of(timezone);
    }

    /** Une journée, heure du restaurant. `date` nul : aujourd'hui. */
    @Transactional(readOnly = true)
    public DayDto day(Restaurant restaurant, LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(timezone) : date;
        Instant from = day.atStartOfDay(timezone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(timezone).toInstant();
        Instant now = Instant.now();
        Instant to = end.isAfter(now) ? now : end;
        List<Zone> tables = zones.findByRestaurantOrderByCodeAsc(restaurant).stream()
                .filter(zone -> zone.getType() == ZoneType.TABLE).toList();
        List<ZoneEvent> history = to.isAfter(from) ? events.findBetween(restaurant, from, to) : List.of();
        return build(day, from, to, tables, history);
    }

    /** Les `count` derniers jours, du plus ancien au plus récent, aujourd'hui compris. */
    @Transactional(readOnly = true)
    public List<DaySummaryDto> lastDays(Restaurant restaurant, int count) {
        LocalDate today = LocalDate.now(timezone);
        List<DaySummaryDto> days = new ArrayList<>();
        for (int back = count - 1; back >= 0; back--) {
            DayDto day = day(restaurant, today.minusDays(back));
            days.add(new DaySummaryDto(day.date(), day.averageWaitSeconds(), day.longestWaitSeconds(), day.seatings(),
                    day.peakOccupancyRate(), day.averageToClearSeconds()));
        }
        return days;
    }

    private record Step(TableState state, Instant at) {}

    static DayDto build(LocalDate date, Instant from, Instant to, List<Zone> tables, List<ZoneEvent> history) {
        Map<UUID, List<ZoneEvent>> byZone = new LinkedHashMap<>();
        for (ZoneEvent event : history) {
            if (event.getZone().getType() == ZoneType.TABLE && event.getState() != null) {
                byZone.computeIfAbsent(event.getZone().getId(), id -> new ArrayList<>()).add(event);
            }
        }

        List<TableTimelineDto> timelines = new ArrayList<>();
        List<Long> waits = new ArrayList<>();
        List<Long> clears = new ArrayList<>();
        List<Instant[]> occupied = new ArrayList<>();  // une entrée par passage de clients : [arrivée, départ]
        Long longestWait = null;
        String longestWaitTable = null;
        Instant longestWaitAt = null;
        Map<Integer, List<Long>> waitsByHour = new LinkedHashMap<>();
        Map<Integer, Integer> seatingsByHour = new LinkedHashMap<>();

        for (Zone table : tables) {
            List<ZoneEvent> tableEvents = byZone.getOrDefault(table.getId(), List.of());
            // Suite des états de la table sur la journée, sans répétition. Un état hérité de la veille
            // n'est pas repris : on ne sait pas quand il a commencé, il fausserait les durées.
            List<Step> steps = new ArrayList<>();
            for (ZoneEvent event : tableEvents) {
                if (steps.isEmpty() || steps.get(steps.size() - 1).state() != event.getState()) {
                    steps.add(new Step(event.getState(), event.getOccurredAt()));
                }
            }

            List<SegmentDto> segments = new ArrayList<>();
            Instant seatedAt = null;
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                boolean closed = i + 1 < steps.size();
                Instant end = closed ? steps.get(i + 1).at() : to;
                TableState next = closed ? steps.get(i + 1).state() : null;
                boolean busy = step.state() == TableState.WAITING || step.state() == TableState.SERVED;
                if (busy && seatedAt == null) {
                    seatedAt = step.at();
                    if (step.at().isAfter(from)) {  // une table déjà occupée à minuit n'est pas une arrivée
                        seatingsByHour.merge(hourOf(from, step.at()), 1, Integer::sum);
                    }
                }
                if (!busy && seatedAt != null) {
                    occupied.add(new Instant[] {seatedAt, step.at()});
                    seatedAt = null;
                }
                if (step.state() == TableState.EMPTY || !end.isAfter(step.at())) {
                    continue;
                }
                segments.add(new SegmentDto(step.state(), step.at(), end));
                long seconds = Duration.between(step.at(), end).getSeconds();
                if (step.state() == TableState.WAITING && next == TableState.SERVED) {
                    waits.add(seconds);
                    waitsByHour.computeIfAbsent(hourOf(from, step.at()), hour -> new ArrayList<>()).add(seconds);
                    if (longestWait == null || seconds > longestWait) {
                        longestWait = seconds;
                        longestWaitTable = table.getCode();
                        longestWaitAt = step.at();
                    }
                } else if (step.state() == TableState.TO_CLEAR && closed) {
                    clears.add(seconds);
                }
            }
            if (seatedAt != null) {
                occupied.add(new Instant[] {seatedAt, to});
            }
            timelines.add(new TableTimelineDto(table.getCode(), segments));
        }

        List<HourDto> hours = new ArrayList<>();
        if (!occupied.isEmpty() && !tables.isEmpty()) {
            Instant first = occupied.stream().map(span -> span[0]).min(Comparator.naturalOrder()).orElse(from);
            Instant last = occupied.stream().map(span -> span[1]).max(Comparator.naturalOrder()).orElse(to);
            int lastHour = Math.max(hourOf(from, first), hourOf(from, last.minusMillis(1)));
            for (int hour = hourOf(from, first); hour <= lastHour; hour++) {
                Instant start = from.plus(Duration.ofHours(hour));
                Instant end = start.plus(Duration.ofHours(1));
                Instant cappedEnd = end.isAfter(to) ? to : end;
                long capacity = Duration.between(start, cappedEnd).getSeconds() * tables.size();
                long used = 0;
                for (Instant[] span : occupied) {
                    Instant a = span[0].isAfter(start) ? span[0] : start;
                    Instant b = span[1].isBefore(cappedEnd) ? span[1] : cappedEnd;
                    if (b.isAfter(a)) {
                        used += Duration.between(a, b).getSeconds();
                    }
                }
                hours.add(new HourDto(hour, capacity <= 0 ? 0 : (double) used / capacity,
                        average(waitsByHour.get(hour)), seatingsByHour.getOrDefault(hour, 0)));
            }
        }

        return new DayDto(date, from, to, tables.size(), timelines, hours,
                average(waits), longestWait == null ? null : longestWait.intValue(), longestWaitTable, longestWaitAt,
                seatingsByHour.values().stream().mapToInt(Integer::intValue).sum(),
                average(clears), peak(occupied, tables.size()));
    }

    /** Heure de la journée (0 à 23) à laquelle tombe un instant. */
    private static int hourOf(Instant dayStart, Instant at) {
        return (int) Math.max(0, Math.min(23, Duration.between(dayStart, at).toHours()));
    }

    private static Integer average(List<Long> seconds) {
        if (seconds == null || seconds.isEmpty()) {
            return null;
        }
        return (int) (seconds.stream().mapToLong(Long::longValue).sum() / seconds.size());
    }

    /** Plus grande part des tables occupées au même moment. */
    private static double peak(List<Instant[]> occupied, int tables) {
        if (tables == 0) {
            return 0;
        }
        int best = 0;
        for (Instant[] span : occupied) {
            int together = 0;
            for (Instant[] other : occupied) {
                if (!other[0].isAfter(span[0]) && other[1].isAfter(span[0])) {
                    together++;
                }
            }
            best = Math.max(best, together);
        }
        return Math.min(1.0, (double) best / tables);
    }
}
