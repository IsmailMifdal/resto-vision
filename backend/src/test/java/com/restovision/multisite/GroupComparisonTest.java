package com.restovision.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restovision.api.Dtos.DaySummaryDto;
import com.restovision.api.Dtos.GroupDto;
import com.restovision.api.Dtos.SiteDto;
import com.restovision.domain.Restaurant;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class GroupComparisonTest {
    @Autowired GroupComparisonService group;

    private static Restaurant withId(Restaurant restaurant) throws ReflectiveOperationException {
        Field id = Restaurant.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(restaurant, UUID.randomUUID());
        return restaurant;
    }

    @Test
    void theWaitIsWeightedByHowManyTablesEachDayServed() throws Exception {
        Restaurant site = withId(new Restaurant("a", "Site A"));
        SiteDto dto = GroupComparisonService.site(site, site, List.of(
                new DaySummaryDto(LocalDate.of(2026, 11, 14), 600, 900, 30, 1.0, 300),   // samedi chargé
                new DaySummaryDto(LocalDate.of(2026, 11, 15), 120, 200, 10, 0.5, 100),   // dimanche calme
                new DaySummaryDto(LocalDate.of(2026, 11, 16), null, null, 0, 0, null)),  // fermé
                25, 1, 1);

        assertThat(dto.seatings()).isEqualTo(40);
        assertThat(dto.averageWaitSeconds()).isEqualTo(480);   // (600 x 30 + 120 x 10) / 40
        assertThat(dto.longestWaitSeconds()).isEqualTo(900);
        assertThat(dto.averageToClearSeconds()).isEqualTo(200);
        assertThat(dto.peakOccupancyRate()).isEqualTo(1.0);
        assertThat(dto.mine()).isTrue();
    }

    @Test
    void theDemoGroupListsItsThreeSitesWithTheirHistory() {
        GroupDto dto = group.compare("demo", 7);

        assertThat(dto.groupCode()).isEqualTo("demo-groupe");
        assertThat(dto.sites()).extracting(SiteDto::code).containsExactlyInAnyOrder("demo", "demo-bastille", "demo-gare");
        assertThat(dto.sites()).filteredOn(SiteDto::mine).extracting(SiteDto::code).containsExactly("demo");
        assertThat(dto.to()).isEqualTo(dto.from().plusDays(6));
    }

    @Test
    void aRestaurantOutsideAnyGroupHasNoComparison() {
        assertThatThrownBy(() -> group.compare("demo-gare-inconnu", 7)).isInstanceOf(ResponseStatusException.class);
    }
}
