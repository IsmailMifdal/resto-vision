package com.restovision.edge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BoxUpdateStatusTest {
    @Autowired EdgeEventService edge;
    @Autowired BoxService boxes;
    @Autowired ReleaseTracker releases;

    @Test
    void aBoxShowsItsVersionTheLatestOneAndItsLastUpdateFailure() {
        releases.onManifest("{\"version\":\"0.3.0\"}".getBytes(StandardCharsets.UTF_8));
        edge.handle(EdgeEvent.heartbeat("demo", "salle-maj", "0.2.0", "signature invalide"));

        var box = boxes.list("demo").stream().filter(b -> b.boxId().equals("salle-maj")).findFirst().orElseThrow();
        assertThat(box.version()).isEqualTo("0.2.0");
        assertThat(box.latestVersion()).isEqualTo("0.3.0");
        assertThat(box.updateError()).isEqualTo("signature invalide");
        releases.onManifest(new byte[0]);
    }
}
