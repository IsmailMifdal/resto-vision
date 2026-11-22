package com.restovision.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.ForecastDto;
import com.restovision.api.Dtos.HourDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeatherForecastTest {
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 17);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);

    private static DayDto day(LocalDate date, int seatings) {
        return new DayDto(date, Instant.EPOCH, Instant.EPOCH, 8, List.of(),
                List.of(new HourDto(20, seatings / 10.0, null, seatings)), null, null, null, null, 0, null, 0);
    }

    @Test
    void daysWithSimilarWeatherCountTwiceAsMuch() {
        // Deux lundis (même poids de jour) : un pluvieux et calme, un sec et rempli. Demain, mardi, il pleut.
        List<DayDto> past = List.of(day(LocalDate.of(2026, 11, 16), 2), day(LocalDate.of(2026, 11, 9), 8));
        Map<LocalDate, DayWeather> weather = Map.of(
                TUESDAY, new DayWeather(11, 6),
                LocalDate.of(2026, 11, 16), new DayWeather(12, 4),
                LocalDate.of(2026, 11, 9), new DayWeather(14, 0));

        ForecastDto withWeather = ForecastService.compute(TUESDAY, past, weather);
        ForecastDto without = ForecastService.compute(TUESDAY, past);

        assertThat(without.hours().get(0).expectedSeatings()).isEqualTo(5.0);        // (2 + 8) / 2
        assertThat(withWeather.hours().get(0).expectedSeatings()).isEqualTo(4.0);    // (2 x 2 + 8) / 3
        assertThat(withWeather.similarWeatherDays()).isEqualTo(1);
        assertThat(withWeather.weather().rainy()).isTrue();
        assertThat(without.weather()).isNull();
    }

    @Test
    void similarMeansSameRainAndWithinFiveDegrees() {
        assertThat(new DayWeather(20, 0).similarTo(new DayWeather(24, 0.5))).isTrue();
        assertThat(new DayWeather(20, 0).similarTo(new DayWeather(26, 0))).isFalse();
        assertThat(new DayWeather(20, 0).similarTo(new DayWeather(20, 3))).isFalse();
    }

    @Test
    void openMeteoDailyAnswerIsReadAndGapsAreSkipped() throws Exception {
        String body = "{\"daily\":{\"time\":[\"2026-11-17\",\"2026-11-18\"],"
                + "\"temperature_2m_max\":[11.4,null],\"precipitation_sum\":[6.2,0.0]}}";
        Map<LocalDate, DayWeather> days = OpenMeteoClient.parse(new ObjectMapper().readTree(body));

        assertThat(days).containsOnlyKeys(TUESDAY);
        assertThat(days.get(TUESDAY)).isEqualTo(new DayWeather(11.4, 6.2));
        assertThat(days).doesNotContainKey(WEDNESDAY);
    }
}
