package com.restovision.live;

import com.restovision.api.Dtos.AlertDto;
import com.restovision.api.Dtos.BoxDto;
import com.restovision.api.Dtos.ZoneDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pousse les changements vers les écrans en direct (WebSocket/STOMP). */
@Component
public class LiveNotifier {
    private final SimpMessagingTemplate template;

    public LiveNotifier(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void zone(String restaurantCode, ZoneDto zone) {
        template.convertAndSend("/topic/restaurants/" + restaurantCode + "/zones", zone);
    }

    public void box(String restaurantCode, BoxDto box) {
        template.convertAndSend("/topic/restaurants/" + restaurantCode + "/boxes", box);
    }

    public void alert(String restaurantCode, AlertDto alert) {
        template.convertAndSend("/topic/restaurants/" + restaurantCode + "/alerts", alert);
    }
}
