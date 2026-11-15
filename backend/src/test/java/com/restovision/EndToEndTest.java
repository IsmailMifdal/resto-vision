package com.restovision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restovision.api.Dtos.LoginResponse;
import com.restovision.api.Dtos.StatsDto;
import com.restovision.api.RestaurantQueryService;
import com.restovision.domain.AlertType;
import com.restovision.domain.TableState;
import com.restovision.edge.EdgeEvent;
import com.restovision.edge.EdgeEventService;
import com.restovision.security.AuthService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

/** Démarre toute l'application sur une base H2 et vérifie la chaîne événement -> état -> alerte. */
@SpringBootTest
class EndToEndTest {
    @Autowired EdgeEventService edge;
    @Autowired RestaurantQueryService queries;
    @Autowired AuthService auth;
    @Autowired JwtDecoder jwtDecoder;

    private static EdgeEvent table(String zone, String state, String previous, Instant at, int persons, int dishes) {
        return new EdgeEvent("TABLE_STATE", "demo", zone, at, state, previous, persons, dishes, null);
    }

    @Test
    void waitingTableRaisesAnAlertThatClosesWhenServed() {
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);
        edge.handle(table("T9", "WAITING", "EMPTY", tenMinutesAgo, 2, 0));

        assertThat(queries.zones("demo"))
                .anyMatch(z -> z.code().equals("T9") && z.state() == TableState.WAITING && z.persons() == 2);
        assertThat(queries.openAlerts("demo"))
                .anyMatch(a -> a.zoneCode().equals("T9") && a.type() == AlertType.UNSERVED);

        edge.handle(table("T9", "SERVED", "WAITING", Instant.now(), 2, 1));

        assertThat(queries.openAlerts("demo")).noneMatch(a -> a.zoneCode().equals("T9"));
        StatsDto stats = queries.statsToday("demo");
        assertThat(stats.tablesOccupied()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void queueAlertFollowsTheNumberOfPeople() {
        edge.handle(new EdgeEvent("QUEUE", "demo", "Q9", Instant.now(), null, null, 6, null, 540));
        assertThat(queries.openAlerts("demo"))
                .anyMatch(a -> a.zoneCode().equals("Q9") && a.type() == AlertType.QUEUE_LENGTH);

        edge.handle(new EdgeEvent("QUEUE", "demo", "Q9", Instant.now(), null, null, 1, null, 90));
        assertThat(queries.openAlerts("demo")).noneMatch(a -> a.zoneCode().equals("Q9"));
    }

    @Test
    void unknownRestaurantIsIgnored() {
        edge.handle(table("T1", "WAITING", "EMPTY", Instant.now(), 2, 0).withRestaurant("inconnu"));
        assertThatThrownBy(() -> queries.zones("inconnu")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void loginIssuesATokenCarryingTheRestaurant() {
        LoginResponse response = auth.login("demo@restovision.local", "demo1234");
        Jwt jwt = jwtDecoder.decode(response.token());
        assertThat(jwt.getClaimAsString("restaurant")).isEqualTo("demo");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("MANAGER");
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatThrownBy(() -> auth.login("demo@restovision.local", "mauvais"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
