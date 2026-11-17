package com.restovision.forecast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Météo par Open-Meteo (open-meteo.com) : gratuit, sans clé. Seules la position du restaurant et
 * la période demandée sont envoyées. Les réponses sont gardées une heure.
 */
@Component
public class OpenMeteoClient implements WeatherClient {
    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);
    private static final Duration KEEP = Duration.ofHours(1);

    private record Cached(Instant at, Map<LocalDate, DayWeather> days) {}

    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String timezone;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public OpenMeteoClient(ObjectMapper mapper,
                           @Value("${restovision.weather.url:https://api.open-meteo.com/v1/forecast}") String baseUrl,
                           @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.timezone = timezone;
    }

    @Override
    public Map<LocalDate, DayWeather> daily(double latitude, double longitude, int pastDays, int forecastDays) {
        String url = String.format(Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f&daily=temperature_2m_max,precipitation_sum&timezone=%s"
                        + "&past_days=%d&forecast_days=%d", baseUrl, latitude, longitude, timezone, pastDays, forecastDays);
        Cached cached = cache.get(url);
        if (cached != null && cached.at().plus(KEEP).isAfter(Instant.now())) {
            return cached.days();
        }
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Météo indisponible : Open-Meteo a répondu {}", response.statusCode());
                return Map.of();
            }
            Map<LocalDate, DayWeather> days = parse(mapper.readTree(response.body()));
            cache.put(url, new Cached(Instant.now(), days));
            return days;
        } catch (Exception e) {
            // Sans météo, la prévision se fait quand même, sur l'historique seul.
            log.warn("Météo indisponible : {}", e.getMessage());
            return Map.of();
        }
    }

    static Map<LocalDate, DayWeather> parse(JsonNode root) {
        JsonNode daily = root.path("daily");
        JsonNode dates = daily.path("time"), temperatures = daily.path("temperature_2m_max"),
                rain = daily.path("precipitation_sum");
        Map<LocalDate, DayWeather> days = new TreeMap<>();
        for (int i = 0; i < dates.size(); i++) {
            if (temperatures.path(i).isNumber() && rain.path(i).isNumber()) {
                days.put(LocalDate.parse(dates.get(i).asText()),
                        new DayWeather(temperatures.get(i).asDouble(), rain.get(i).asDouble()));
            }
        }
        return days;
    }
}
