package com.restovision.edge;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.api.Dtos.BoxDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BoxServiceTest {
    @Autowired EdgeEventService edge;
    @Autowired BoxService boxes;

    private BoxDto box(String id) {
        return boxes.list("demo").stream().filter(b -> b.boxId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void aHeartbeatPutsTheBoxOnlineWithItsRate() {
        edge.handle(EdgeEvent.heartbeat("demo", "salle-1", "caméra", 4.8, 120));

        BoxDto box = box("salle-1");
        assertThat(box.online()).isTrue();
        assertThat(box.fps()).isEqualTo(4.8);
        assertThat(box.source()).isEqualTo("caméra");
        assertThat(box.uptimeSeconds()).isEqualTo(120);
    }

    @Test
    void aSilentBoxGoesOfflineThenComesBack() {
        edge.handle(EdgeEvent.heartbeat("demo", "salle-2", "caméra", 5, 10));

        boxes.checkSilentBoxes(Instant.now().plusSeconds(60));
        assertThat(box("salle-2").online()).isTrue();    // 60 s de silence : encore en ligne
        boxes.checkSilentBoxes(Instant.now().plusSeconds(120));
        assertThat(box("salle-2").online()).isFalse();   // plus de 90 s : hors ligne

        edge.handle(EdgeEvent.heartbeat("demo", "salle-2", "caméra", 5, 5));
        assertThat(box("salle-2").online()).isTrue();
    }

    @Test
    void aHeartbeatFromAnUnknownRestaurantIsIgnored() {
        edge.handle(EdgeEvent.heartbeat("inconnu", "salle-3", "caméra", 5, 10));
        assertThat(boxes.list("demo")).noneMatch(b -> b.boxId().equals("salle-3"));
    }
}
