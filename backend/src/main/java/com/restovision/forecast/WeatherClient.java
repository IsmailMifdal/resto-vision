package com.restovision.forecast;

import java.time.LocalDate;
import java.util.Map;

/** Source de météo : les jours passés et les prochains jours, à une position donnée. */
public interface WeatherClient {
    /** Météo par jour, de `pastDays` jours avant aujourd'hui jusqu'à `forecastDays` jours après. Map vide si indisponible. */
    Map<LocalDate, DayWeather> daily(double latitude, double longitude, int pastDays, int forecastDays);
}
