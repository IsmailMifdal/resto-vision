package com.restovision.edge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/** Message JSON publié par le boîtier (voir edge/restovision_edge/models.py). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeEvent(String type, String restaurantId, String zoneId, Instant timestamp,
                        String state, String previousState, Integer persons, Integer dishes,
                        Integer estimatedWaitSeconds, List<ZoneLayout> zones,
                        String boxId, String source, Double fps, String version, Long uptimeSeconds,
                        Integer buffered, Integer servedFood, Integer leftFood) {

    /** Contour d'une zone dans l'image de la caméra. Présent seulement dans un message de type LAYOUT. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZoneLayout(String id, String type, List<List<Double>> polygon) {}

    /** Événement d'état, sans plan. */
    public EdgeEvent(String type, String restaurantId, String zoneId, Instant timestamp, String state,
                     String previousState, Integer persons, Integer dishes, Integer estimatedWaitSeconds) {
        this(type, restaurantId, zoneId, timestamp, state, previousState, persons, dishes, estimatedWaitSeconds, null,
                null, null, null, null, null, null, null, null);
    }

    public static EdgeEvent layout(String restaurantId, List<ZoneLayout> zones) {
        return new EdgeEvent("LAYOUT", restaurantId, null, Instant.now(), null, null, null, null, null, zones,
                null, null, null, null, null, null, null, null);
    }

    /** Signal de vie d'un boîtier. */
    public static EdgeEvent heartbeat(String restaurantId, String boxId, String source, double fps, long uptimeSeconds) {
        return new EdgeEvent("HEARTBEAT", restaurantId, null, Instant.now(), null, null, null, null, null, null,
                boxId, source, fps, "test", uptimeSeconds, 0, null, null);
    }

    /** Départ des clients d'une table : aliments vus pendant le repas et aliments restants. */
    public static EdgeEvent plateReturn(String restaurantId, String zoneId, Instant at, int servedFood, int leftFood) {
        return new EdgeEvent("PLATE_RETURN", restaurantId, zoneId, at, null, null, null, null, null, null,
                null, null, null, null, null, null, servedFood, leftFood);
    }

    public EdgeEvent withRestaurant(String otherRestaurantId) {
        return new EdgeEvent(type, otherRestaurantId, zoneId, timestamp, state, previousState, persons, dishes,
                estimatedWaitSeconds, zones, boxId, source, fps, version, uptimeSeconds, buffered, servedFood, leftFood);
    }
}
