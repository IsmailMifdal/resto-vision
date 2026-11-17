package com.restovision.forecast;

import com.restovision.api.Dtos.WeatherDto;
import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.ForecastDto;
import com.restovision.api.Dtos.ForecastHourDto;
import com.restovision.api.Dtos.HourDto;
import com.restovision.domain.Restaurant;
import com.restovision.stats.HistoryService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prédiction d'affluence : à quoi s'attendre, heure par heure, un jour à venir.
 *
 * La méthode est volontairement simple et lisible : la moyenne des journées passées, où le même jour
 * de la semaine compte trois fois plus que les autres. Si la position du restaurant est connue, les jours
 * dont la météo ressemble à celle du jour visé comptent en plus deux fois plus. Aucun coefficient n'est
 * inventé : ce sont les journées du restaurant qui disent comment il réagit au temps qu'il fait.
 * Les réservations ne sont pas prises en compte.
 */
@Service
public class ForecastService {
    /** Jours d'historique pris en compte. */
    static final int WINDOW_DAYS = 28;
    /** Poids d'une journée du même jour de la semaine, contre 1 pour les autres. */
    static final int SAME_WEEKDAY_WEIGHT = 3;
    /** Multiplicateur d'une journée à la météo semblable. */
    static final int SIMILAR_WEATHER_WEIGHT = 2;

    private final HistoryService history;
    private final WeatherClient weather;
    private final ZoneId timezone;

    public ForecastService(HistoryService history, WeatherClient weather,
                           @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.history = history;
        this.weather = weather;
        this.timezone = ZoneId.of(timezone);
    }

    /** `date` nul : demain. La journée en cours n'entre pas dans le calcul, elle n'est pas terminée. */
    @Transactional(readOnly = true)
    public ForecastDto forecast(Restaurant restaurant, LocalDate date) {
        LocalDate today = LocalDate.now(timezone);
        LocalDate target = date == null ? today.plusDays(1) : date;
        List<DayDto> past = new ArrayList<>();
        for (int back = 1; back <= WINDOW_DAYS; back++) {
            past.add(history.day(restaurant, today.minusDays(back)));
        }
        Map<LocalDate, DayWeather> days = Map.of();
        int ahead = (int) java.time.temporal.ChronoUnit.DAYS.between(today, target);
        if (restaurant.hasLocation() && ahead >= 0 && ahead <= 14) {
            days = weather.daily(restaurant.getLatitude(), restaurant.getLongitude(), WINDOW_DAYS, ahead + 1);
        }
        return compute(target, past, days);
    }

    static ForecastDto compute(LocalDate target, List<DayDto> past) {
        return compute(target, past, Map.of());
    }

    static ForecastDto compute(LocalDate target, List<DayDto> past, Map<LocalDate, DayWeather> weather) {
        DayWeather expected = weather.get(target);
        List<DayDto> served = past.stream().filter(day -> !day.hours().isEmpty()).toList();
        int sameWeekday = (int) served.stream().filter(day -> day.date().getDayOfWeek() == target.getDayOfWeek()).count();
        if (served.isEmpty()) {
            return new ForecastDto(target, 0, 0, List.of(), null, 0, WeatherDto.of(expected), 0);
        }
        Map<Integer, double[]> sums = new TreeMap<>();  // heure -> [tablées pondérées, occupation pondérée]
        double totalWeight = 0;
        int similarWeather = 0;
        for (DayDto day : served) {
            int weight = day.date().getDayOfWeek() == target.getDayOfWeek() ? SAME_WEEKDAY_WEIGHT : 1;
            DayWeather then = weather.get(day.date());
            if (expected != null && then != null && then.similarTo(expected)) {
                weight *= SIMILAR_WEATHER_WEIGHT;
                similarWeather++;
            }
            totalWeight += weight;
            for (HourDto hour : day.hours()) {
                double[] sum = sums.computeIfAbsent(hour.hour(), h -> new double[2]);
                sum[0] += weight * hour.seatings();
                sum[1] += weight * hour.occupancyRate();
            }
        }
        // Une heure sans service un jour donné compte pour zéro ce jour-là : on divise par le poids de tous les jours.
        double weight = totalWeight;
        List<ForecastHourDto> hours = sums.entrySet().stream()
                .map(entry -> new ForecastHourDto(entry.getKey(),
                        Math.round(entry.getValue()[0] / weight * 10) / 10.0,
                        Math.round(entry.getValue()[1] / weight * 100) / 100.0))
                .filter(hour -> hour.expectedSeatings() >= 0.1 || hour.expectedOccupancyRate() >= 0.05)
                .collect(Collectors.toList());
        Integer peak = hours.stream().max(Comparator.comparingDouble(ForecastHourDto::expectedOccupancyRate))
                .map(ForecastHourDto::hour).orElse(null);
        int seatings = (int) Math.round(hours.stream().mapToDouble(ForecastHourDto::expectedSeatings).sum());
        return new ForecastDto(target, served.size(), sameWeekday, hours, peak, seatings, WeatherDto.of(expected),
                similarWeather);
    }
}
