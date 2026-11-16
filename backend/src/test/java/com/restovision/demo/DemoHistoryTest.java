package com.restovision.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.domain.Restaurant;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneEvent;
import com.restovision.domain.ZoneType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoHistoryTest {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private final Zone table = new Zone(new Restaurant("demo", "Démo"), "T1", ZoneType.TABLE);

    @Test
    void aPastDayIsMadeOfCompleteVisitsInOrder() {
        LocalDate day = LocalDate.of(2026, 11, 13);
        List<ZoneEvent> events = DemoDataSeeder.serviceDay(table, 1, day, PARIS, Instant.parse("2026-11-20T00:00:00Z"));

        assertThat(events).isNotEmpty();
        assertThat(events.size() % 4).isZero();
        assertThat(events).isSortedAccordingTo((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()));
        assertThat(events.get(events.size() - 1).getState()).isEqualTo(TableState.EMPTY);
        assertThat(events).allMatch(e -> LocalDate.ofInstant(e.getOccurredAt(), PARIS).equals(day));
    }

    @Test
    void theSameDayIsAlwaysTheSameAndNothingIsInventedAfterNow() {
        LocalDate day = LocalDate.of(2026, 11, 13);
        Instant lunchTime = day.atTime(13, 0).atZone(PARIS).toInstant();
        Instant later = Instant.parse("2026-11-20T00:00:00Z");

        assertThat(DemoDataSeeder.serviceDay(table, 1, day, PARIS, later))
                .extracting(ZoneEvent::getOccurredAt)
                .isEqualTo(DemoDataSeeder.serviceDay(table, 1, day, PARIS, later).stream()
                        .map(ZoneEvent::getOccurredAt).toList());
        assertThat(DemoDataSeeder.serviceDay(table, 1, day, PARIS, lunchTime))
                .allMatch(e -> !e.getOccurredAt().isAfter(lunchTime));
    }
}
