package com.restovision.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.HourDto;
import com.restovision.api.Dtos.SegmentDto;
import com.restovision.domain.Restaurant;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneEvent;
import com.restovision.domain.ZoneType;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);
    private static final Instant MIDNIGHT = Instant.parse("2026-11-13T23:00:00Z");  // minuit à Paris
    private static final Instant END = MIDNIGHT.plusSeconds(24 * 3600);
    private final Restaurant restaurant = new Restaurant("demo", "Démo");
    private final Zone t1 = table("T1");
    private final Zone t2 = table("T2");

    private Zone table(String code) {
        Zone zone = new Zone(restaurant, code, ZoneType.TABLE);
        try {
            Field id = Zone.class.getDeclaredField("id");  // l'identifiant est normalement donné par la base
            id.setAccessible(true);
            id.set(zone, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return zone;
    }

    /** Heure du restaurant, en heures et minutes après minuit. */
    private static Instant at(int hour, int minute) {
        return MIDNIGHT.plusSeconds(hour * 3600L + minute * 60L);
    }

    private static ZoneEvent event(Zone zone, TableState state, TableState previous, Instant at) {
        return new ZoneEvent(zone, state, previous, 2, 0, at);
    }

    @Test
    void aFullCycleGivesSegmentsWaitAndClearTimes() {
        DayDto day = HistoryService.build(DAY, MIDNIGHT, END, List.of(t1, t2), List.of(
                event(t1, TableState.WAITING, TableState.EMPTY, at(12, 0)),
                event(t1, TableState.SERVED, TableState.WAITING, at(12, 6)),
                event(t1, TableState.TO_CLEAR, TableState.SERVED, at(13, 0)),
                event(t1, TableState.EMPTY, TableState.TO_CLEAR, at(13, 4))));

        assertThat(day.tablesTotal()).isEqualTo(2);
        assertThat(day.tables().get(0).segments()).extracting(SegmentDto::state)
                .containsExactly(TableState.WAITING, TableState.SERVED, TableState.TO_CLEAR);
        assertThat(day.tables().get(1).segments()).isEmpty();
        assertThat(day.averageWaitSeconds()).isEqualTo(360);
        assertThat(day.longestWaitSeconds()).isEqualTo(360);
        assertThat(day.longestWaitTable()).isEqualTo("T1");
        assertThat(day.longestWaitAt()).isEqualTo(at(12, 0));
        assertThat(day.averageToClearSeconds()).isEqualTo(240);
        assertThat(day.seatings()).isEqualTo(1);
        assertThat(day.peakOccupancyRate()).isEqualTo(0.5);
    }

    @Test
    void hoursCoverTheServiceAndOccupancyIsTheShareOfTableTime() {
        DayDto day = HistoryService.build(DAY, MIDNIGHT, END, List.of(t1, t2), List.of(
                event(t1, TableState.WAITING, TableState.EMPTY, at(12, 0)),
                event(t2, TableState.WAITING, TableState.EMPTY, at(12, 30)),
                event(t1, TableState.SERVED, TableState.WAITING, at(12, 10)),
                event(t2, TableState.SERVED, TableState.WAITING, at(12, 50)),
                event(t1, TableState.TO_CLEAR, TableState.SERVED, at(13, 30)),
                event(t2, TableState.TO_CLEAR, TableState.SERVED, at(13, 30))));

        assertThat(day.hours()).extracting(HourDto::hour).containsExactly(12, 13);
        HourDto noon = day.hours().get(0);
        assertThat(noon.occupancyRate()).isEqualTo(0.75);   // T1 toute l'heure, T2 la moitié
        assertThat(noon.seatings()).isEqualTo(2);
        assertThat(noon.averageWaitSeconds()).isEqualTo(900);  // 10 min et 20 min
        assertThat(day.hours().get(1).occupancyRate()).isEqualTo(0.5);
        assertThat(day.hours().get(1).averageWaitSeconds()).isNull();
        assertThat(day.peakOccupancyRate()).isEqualTo(1.0);
        assertThat(day.seatings()).isEqualTo(2);
    }

    @Test
    void anOpenStateStopsAtNowAndIsNotCountedAsAWaitOrAClearing() {
        Instant now = at(12, 20);
        DayDto day = HistoryService.build(DAY, MIDNIGHT, now, List.of(t1), List.of(
                event(t1, TableState.WAITING, TableState.EMPTY, at(12, 0))));

        SegmentDto open = day.tables().get(0).segments().get(0);
        assertThat(open.end()).isEqualTo(now);
        assertThat(day.averageWaitSeconds()).isNull();   // pas encore servie : l'attente n'est pas terminée
        assertThat(day.seatings()).isEqualTo(1);
        assertThat(day.hours().get(0).occupancyRate()).isEqualTo(1.0);  // sur les 20 minutes écoulées
    }

    @Test
    void aDayWithoutEventsIsEmptyNotBroken() {
        DayDto day = HistoryService.build(DAY, MIDNIGHT, END, List.of(t1), List.of());

        assertThat(day.hours()).isEmpty();
        assertThat(day.averageWaitSeconds()).isNull();
        assertThat(day.seatings()).isZero();
        assertThat(day.peakOccupancyRate()).isZero();
    }
}
