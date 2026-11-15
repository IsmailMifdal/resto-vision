package com.restovision.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.domain.AlertType;
import com.restovision.domain.Restaurant;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertConditionTest {
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private final Restaurant restaurant = new Restaurant("demo", "Demo");

    private Zone table(TableState state, long secondsAgo) {
        Zone zone = new Zone(restaurant, "T1", ZoneType.TABLE);
        zone.setState(state);
        zone.setStateSince(NOW.minusSeconds(secondsAgo));
        return zone;
    }

    @Test
    void unservedTriggersOnlyAfterThreshold() {
        assertThat(AlertService.isTriggered(AlertType.UNSERVED, 300, table(TableState.WAITING, 299), NOW)).isFalse();
        assertThat(AlertService.isTriggered(AlertType.UNSERVED, 300, table(TableState.WAITING, 300), NOW)).isTrue();
    }

    @Test
    void unservedIgnoresOtherStates() {
        assertThat(AlertService.isTriggered(AlertType.UNSERVED, 300, table(TableState.SERVED, 900), NOW)).isFalse();
        assertThat(AlertService.isTriggered(AlertType.UNSERVED, 300, table(TableState.EMPTY, 900), NOW)).isFalse();
    }

    @Test
    void toClearTriggersAfterThreshold() {
        assertThat(AlertService.isTriggered(AlertType.TO_CLEAR, 120, table(TableState.TO_CLEAR, 121), NOW)).isTrue();
        assertThat(AlertService.isTriggered(AlertType.TO_CLEAR, 120, table(TableState.WAITING, 900), NOW)).isFalse();
    }

    @Test
    void queueLengthComparesPersons() {
        Zone queue = new Zone(restaurant, "Q1", ZoneType.QUEUE);
        queue.setPersons(4);
        assertThat(AlertService.isTriggered(AlertType.QUEUE_LENGTH, 5, queue, NOW)).isFalse();
        queue.setPersons(5);
        assertThat(AlertService.isTriggered(AlertType.QUEUE_LENGTH, 5, queue, NOW)).isTrue();
    }

    @Test
    void messageIsReadableByStaff() {
        assertThat(AlertService.message(AlertType.UNSERVED, table(TableState.WAITING, 360), NOW))
                .isEqualTo("Table T1 : clients non servis depuis 6 min");
    }
}
