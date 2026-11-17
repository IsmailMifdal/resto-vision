package com.restovision;

import com.restovision.api.Dtos.ReportDto;
import java.time.LocalDate;
import java.time.ZoneId;
import com.restovision.api.Dtos.DayDto;
import java.util.List;
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
    void zoneLayoutFromTheBoxIsExposedForTheFloorPlan() {
        List<List<Double>> square = List.of(List.of(100.0, 300.0), List.of(400.0, 300.0), List.of(400.0, 600.0),
                List.of(100.0, 600.0));
        edge.handle(EdgeEvent.layout("demo", List.of(
                new EdgeEvent.ZoneLayout("T7", "TABLE", square),
                new EdgeEvent.ZoneLayout("T77", "TABLE", List.of(List.of(1.0, 2.0))))));  // contour incomplet
        assertThat(queries.zones("demo"))
                .anyMatch(z -> z.code().equals("T7") && z.state() == TableState.EMPTY && square.equals(z.polygon()));
        assertThat(queries.zones("demo")).noneMatch(z -> z.code().equals("T77"));
    }

    @Test
    void aDayOfHistoryIsReadableThroughTheApi() {
        edge.handle(table("T6", "WAITING", "EMPTY", Instant.now().minusSeconds(300), 2, 0));
        edge.handle(table("T6", "SERVED", "WAITING", Instant.now().minusSeconds(60), 2, 2));
        DayDto day = queries.day("demo", null);
        assertThat(day.tables()).anyMatch(t -> t.code().equals("T6") && !t.segments().isEmpty());
        assertThat(queries.lastDays("demo", 7)).hasSize(7);
        assertThat(queries.lastDays("demo", 7).get(6).date()).isEqualTo(day.date());
    }

    @Test
    void aFinishedDayGetsItsReportOnceAndKeepsIt() {
        Instant yesterdayNoon = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1).atTime(12, 0)
                .atZone(ZoneId.of("Europe/Paris")).toInstant();
        edge.handle(table("T5", "WAITING", "EMPTY", yesterdayNoon, 2, 0));
        edge.handle(table("T5", "SERVED", "WAITING", yesterdayNoon.plusSeconds(420), 2, 2));
        edge.handle(table("T5", "EMPTY", "SERVED", yesterdayNoon.plusSeconds(3600), 0, 0));
        LocalDate yesterday = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1);

        ReportDto first = queries.dailyReport("demo", yesterday, false);
        assertThat(first.partial()).isFalse();
        assertThat(first.aiAvailable()).isFalse();   // pas de clé d'API dans les tests
        assertThat(first.text()).contains("7 min avant d'être servis");
        assertThat(queries.dailyReport("demo", yesterday, false).generatedAt()).isEqualTo(first.generatedAt());
        assertThat(queries.dailyReport("demo", yesterday, true).generatedAt()).isAfterOrEqualTo(first.generatedAt());

        assertThat(queries.dailyReport("demo", null, false).partial()).isTrue();
    }

    @Autowired com.restovision.waste.WasteService waste;

    @Test
    void aPlateReturnFromTheBoxShowsInTheDayWaste() {
        edge.handle(table("T3", "SERVED", "WAITING", Instant.now().minusSeconds(1200), 2, 2));
        edge.handle(EdgeEvent.plateReturn("demo", "T3", Instant.now().minusSeconds(60), 2, 1));
        edge.handle(EdgeEvent.plateReturn("demo", "T99", Instant.now(), 2, 1));   // table inconnue : ignorée
        assertThat(waste.day("demo", null).leftovers())
                .anyMatch(r -> r.table().equals("T3") && r.leftFood() == 1)
                .noneMatch(r -> r.table().equals("T99"));
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
