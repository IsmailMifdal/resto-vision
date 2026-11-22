package com.restovision.edge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReleaseTrackerTest {
    @Test
    void theLatestPublishedVersionIsKeptAndAClearedMessageForgetsIt() {
        ReleaseTracker tracker = new ReleaseTracker(new ObjectMapper());
        tracker.onManifest("{\"version\":\"0.3.0\",\"archive\":\"...\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(tracker.latest()).isEqualTo("0.3.0");
        tracker.onManifest("pas du json".getBytes(StandardCharsets.UTF_8));
        assertThat(tracker.latest()).isEqualTo("0.3.0");
        tracker.onManifest("{\"version\":\"<script>\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(tracker.latest()).isEqualTo("0.3.0");
        tracker.onManifest(new byte[0]);
        assertThat(tracker.latest()).isNull();
    }
}
