package com.restovision.forecast;

/** Météo d'une journée : température maximale (°C) et pluie (mm). */
public record DayWeather(double maxTemperature, double precipitation) {
    /** À partir d'un millimètre, on compte la journée comme pluvieuse. */
    public boolean rainy() {
        return precipitation >= 1.0;
    }

    /** Deux journées se ressemblent si elles sont toutes deux pluvieuses ou non, à 5 °C près. */
    public boolean similarTo(DayWeather other) {
        return rainy() == other.rainy() && Math.abs(maxTemperature - other.maxTemperature) <= 5.0;
    }
}
