package com.restovision.multisite;

import com.restovision.api.Dtos.BoxDto;
import com.restovision.api.Dtos.DaySummaryDto;
import com.restovision.api.Dtos.GroupDto;
import com.restovision.api.Dtos.SiteDto;
import com.restovision.domain.Restaurant;
import com.restovision.edge.BoxService;
import com.restovision.repo.RestaurantRepository;
import com.restovision.stats.HistoryService;
import com.restovision.waste.WasteService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Comparaison des restaurants d'un même groupe (Restaurant.groupCode) sur les derniers jours.
 * Réservée au responsable de groupe (rôle ADMIN) : un gérant ne voit que son restaurant.
 */
@Service
public class GroupComparisonService {
    private final RestaurantRepository restaurants;
    private final HistoryService history;
    private final WasteService waste;
    private final BoxService boxes;
    private final ZoneId timezone;

    public GroupComparisonService(RestaurantRepository restaurants, HistoryService history, WasteService waste,
                                  BoxService boxes, @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.restaurants = restaurants;
        this.history = history;
        this.waste = waste;
        this.boxes = boxes;
        this.timezone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public GroupDto compare(String restaurantCode, int days) {
        Restaurant own = restaurants.findByCode(restaurantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        if (own.getGroupCode() == null || own.getGroupCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ce restaurant ne fait partie d'aucun groupe.");
        }
        int count = Math.max(1, Math.min(31, days));
        // Les jours terminés seulement : la journée en cours fausserait la comparaison.
        LocalDate last = LocalDate.now(timezone).minusDays(1);
        List<SiteDto> sites = new ArrayList<>();
        for (Restaurant site : restaurants.findByGroupCodeOrderByNameAsc(own.getGroupCode())) {
            List<DaySummaryDto> summaries = history.lastDays(site, count + 1).subList(0, count);
            List<BoxDto> siteBoxes = boxes.list(site.getCode());
            sites.add(site(site, own, summaries, waste.shareBetween(site, last.minusDays(count - 1), last),
                    (int) siteBoxes.stream().filter(BoxDto::online).count(), siteBoxes.size()));
        }
        return new GroupDto(own.getGroupCode(), count, last.minusDays(count - 1), last, sites);
    }

    static SiteDto site(Restaurant site, Restaurant own, List<DaySummaryDto> days, Integer leftoverShare,
                        int boxesOnline, int boxesTotal) {
        int seatings = days.stream().mapToInt(DaySummaryDto::seatings).sum();
        // Moyenne des jours pondérée par leurs tablées : un jour calme ne pèse pas autant qu'un samedi.
        long weightedWait = 0, weight = 0;
        for (DaySummaryDto day : days) {
            if (day.averageWaitSeconds() != null && day.seatings() > 0) {
                weightedWait += (long) day.averageWaitSeconds() * day.seatings();
                weight += day.seatings();
            }
        }
        Integer longest = days.stream().map(DaySummaryDto::longestWaitSeconds).filter(Objects::nonNull)
                .max(Integer::compare).orElse(null);
        List<Integer> clearing = days.stream().map(DaySummaryDto::averageToClearSeconds).filter(Objects::nonNull).toList();
        double peak = days.stream().mapToDouble(DaySummaryDto::peakOccupancyRate).max().orElse(0);
        return new SiteDto(site.getCode(), site.getName(), site.getId().equals(own.getId()), seatings,
                weight == 0 ? null : (int) (weightedWait / weight), longest,
                clearing.isEmpty() ? null : (int) clearing.stream().mapToInt(Integer::intValue).average().orElse(0),
                Math.round(peak * 100) / 100.0, leftoverShare, boxesOnline, boxesTotal);
    }
}
