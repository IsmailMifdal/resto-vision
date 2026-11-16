package com.restovision.edge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/** Message JSON publié par le boîtier (voir edge/restovision_edge/models.py). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeEvent(String type, String restaurantId, String zoneId, Instant timestamp,
                        String state, String previousState, Integer persons, Integer dishes,
                        Integer estimatedWaitSeconds, List<ZoneLayout> zones) {

    /** Contour d'une zone dans l'image de la caméra. Présent seulement dans un message de type LAYOUT. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZoneLayout(String id, String type, List<List<Double>> polygon) {}

    /** Événement d'état, sans plan. */
    public EdgeEvent(String type, String restaurantId, String zoneId, Instant timestamp, String state,
                     String previousState, Integer persons, Integer dishes, Integer estimatedWaitSeconds) {
        this(type, restaurantId, zoneId, timestamp, state, previousState, persons, dishes, estimatedWaitSeconds, null);
    }

    public static EdgeEvent layout(String restaurantId, List<ZoneLayout> zones) {
        return new EdgeEvent("LAYOUT", restaurantId, null, Instant.now(), null, null, null, null, null, zones);
    }

    public EdgeEvent withRestaurant(String otherRestaurantId) {
        return new EdgeEvent(type, otherRestaurantId, zoneId, timestamp, state, previousState, persons, dishes,
                estimatedWaitSeconds, zones);
    }
}
