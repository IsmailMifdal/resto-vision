package com.restovision.waste;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.api.Dtos.WasteDayDto;
import com.restovision.domain.Restaurant;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WasteServiceTest {
    private static final Instant NOON = Instant.parse("2026-11-14T11:00:00Z");
    private final Zone t4 = new Zone(new Restaurant("demo", "Démo"), "T4", ZoneType.TABLE);

    @Test
    void aDayCountsTheMealsThatLeftFoodAndComparesWithTheWeek() {
        WasteDayDto day = WasteService.build(LocalDate.of(2026, 11, 14), NOON, List.of(
                new PlateReturn(t4, NOON, 3, 0),
                new PlateReturn(t4, NOON.plusSeconds(3600), 2, 1),
                new PlateReturn(t4, NOON.plusSeconds(7200), 4, 0),
                new PlateReturn(t4, NOON.plusSeconds(9000), 2, 0)), List.of(10, 20));

        assertThat(day.meals()).isEqualTo(4);
        assertThat(day.withLeftovers()).isEqualTo(1);
        assertThat(day.leftoverShare()).isEqualTo(25);
        assertThat(day.previousWeekShare()).isEqualTo(15);
        assertThat(day.leftovers()).singleElement().satisfies(r -> {
            assertThat(r.table()).isEqualTo("T4");
            assertThat(r.leftFood()).isEqualTo(1);
        });
    }

    @Test
    void aDayWithoutObservedMealsHasNoShareRatherThanZero() {
        WasteDayDto day = WasteService.build(LocalDate.of(2026, 11, 14), NOON, List.of(), List.of());
        assertThat(day.meals()).isZero();
        assertThat(day.leftoverShare()).isNull();
        assertThat(day.previousWeekShare()).isNull();
    }

    @Test
    void moreLeftThanServedIsCappedAtWhatWasServed() {
        assertThat(new PlateReturn(t4, NOON, 2, 5).getLeftFood()).isEqualTo(2);
    }
}
