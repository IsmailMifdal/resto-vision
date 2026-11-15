package com.restovision.edge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Message JSON publié par le boîtier (voir edge/restovision_edge/models.py). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeEvent(String type, String restaurantId, String zoneId, Instant timestamp,
                        String state, String previousState, Integer persons, Integer dishes,
                        Integer estimatedWaitSeconds) {

    public EdgeEvent withRestaurant(String otherRestaurantId) {
        return new EdgeEvent(type, otherRestaurantId, zoneId, timestamp, state, previousState, persons, dishes,
                estimatedWaitSeconds);
    }
}
