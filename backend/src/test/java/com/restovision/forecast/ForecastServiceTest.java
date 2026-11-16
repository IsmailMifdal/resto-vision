package com.restovision.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.ForecastDto;
import com.restovision.api.Dtos.HourDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastServiceTest {
    private static final LocalDate NEXT_SATURDAY = LocalDate.of(2026, 11, 21);

    private static DayDto day(LocalDate date, HourDto... hours) {
        return new DayDto(date, Instant.EPOCH, Instant.EPOCH, 8, List.of(), List.of(hours), null, null, null, null,
                0, null, 0);
    }

    @Test
    void theSameWeekdayWeighsThreeTimesMore() {
        ForecastDto forecast = ForecastService.compute(NEXT_SATURDAY, List.of(
                day(LocalDate.of(2026, 11, 14), new HourDto(20, 1.0, null, 10)),   // samedi
                day(LocalDate.of(2026, 11, 13), new HourDto(20, 0.5, null, 5)),    // vendredi
                day(LocalDate.of(2026, 11, 12), new HourDto(20, 0.5, null, 5))));  // jeudi

        assertThat(forecast.basedOnDays()).isEqualTo(3);
        assertThat(forecast.sameWeekdayDays()).isEqualTo(1);
        // (3 x 10 + 5 + 5) / 5 = 8 tablées ; (3 x 1,0 + 0,5 + 0,5) / 5 = 0,8
        assertThat(forecast.hours()).hasSize(1);
        assertThat(forecast.hours().get(0).expectedSeatings()).isEqualTo(8.0);
        assertThat(forecast.hours().get(0).expectedOccupancyRate()).isEqualTo(0.8);
        assertThat(forecast.peakHour()).isEqualTo(20);
        assertThat(forecast.expectedSeatings()).isEqualTo(8);
    }

    @Test
    void anHourServedOnlySomeDaysIsDilutedNotIgnored() {
        ForecastDto forecast = ForecastService.compute(LocalDate.of(2026, 11, 18), List.of(   // mercredi
                day(LocalDate.of(2026, 11, 14), new HourDto(12, 0.6, null, 6), new HourDto(22, 0.4, null, 4)),
                day(LocalDate.of(2026, 11, 13), new HourDto(12, 0.6, null, 6))));

        assertThat(forecast.hours()).extracting("hour").containsExactly(12, 22);
        assertThat(forecast.hours().get(1).expectedSeatings()).isEqualTo(2.0);   // 4 un jour sur deux
        assertThat(forecast.peakHour()).isEqualTo(12);
    }

    @Test
    void daysWithoutServiceAreNotCountedAndNoHistoryGivesNoForecast() {
        ForecastDto withClosedDay = ForecastService.compute(NEXT_SATURDAY, List.of(
                day(LocalDate.of(2026, 11, 14), new HourDto(20, 1.0, null, 10)),
                day(LocalDate.of(2026, 11, 15))));
        assertThat(withClosedDay.basedOnDays()).isEqualTo(1);
        assertThat(withClosedDay.hours().get(0).expectedSeatings()).isEqualTo(10.0);

        ForecastDto empty = ForecastService.compute(NEXT_SATURDAY, List.of(day(LocalDate.of(2026, 11, 15))));
        assertThat(empty.basedOnDays()).isZero();
        assertThat(empty.hours()).isEmpty();
        assertThat(empty.peakHour()).isNull();
    }
}
