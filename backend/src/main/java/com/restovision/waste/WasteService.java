package com.restovision.waste;

import com.restovision.api.Dtos.PlateReturnDto;
import com.restovision.api.Dtos.WasteDayDto;
import com.restovision.domain.Restaurant;
import com.restovision.repo.RestaurantRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Ce qui revient en cuisine : par jour, la part des repas qui laissent de la nourriture. */
@Service
public class WasteService {
    private final RestaurantRepository restaurants;
    private final PlateReturnRepository returns;
    private final ZoneId timezone;

    public WasteService(RestaurantRepository restaurants, PlateReturnRepository returns,
                        @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.restaurants = restaurants;
        this.returns = returns;
        this.timezone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public WasteDayDto day(String restaurantCode, LocalDate date) {
        Restaurant restaurant = restaurant(restaurantCode);
        LocalDate day = date == null ? LocalDate.now(timezone) : date;
        Instant from = day.atStartOfDay(timezone).toInstant();
        List<PlateReturn> found = returns.findBetween(restaurant, from, day.plusDays(1).atStartOfDay(timezone).toInstant());
        List<Integer> weekShares = new ArrayList<>();
        for (int back = 7; back >= 1; back--) {
            LocalDate other = day.minusDays(back);
            Integer share = share(returns.findBetween(restaurant, other.atStartOfDay(timezone).toInstant(),
                    other.plusDays(1).atStartOfDay(timezone).toInstant()));
            if (share != null) {
                weekShares.add(share);
            }
        }
        return build(day, from, found, weekShares);
    }

    static WasteDayDto build(LocalDate day, Instant dayStart, List<PlateReturn> found, List<Integer> previousShares) {
        int withLeftovers = (int) found.stream().filter(r -> r.getLeftFood() > 0).count();
        Integer weekShare = previousShares.isEmpty() ? null
                : (int) Math.round(previousShares.stream().mapToInt(Integer::intValue).average().orElse(0));
        List<PlateReturnDto> leftovers = found.stream().filter(r -> r.getLeftFood() > 0)
                .map(r -> new PlateReturnDto(r.getZone().getCode(), r.getOccurredAt(), r.getServedFood(), r.getLeftFood()))
                .toList();
        return new WasteDayDto(day, dayStart, found.size(), withLeftovers, share(found), weekShare, leftovers);
    }

    /** Part des repas qui ont laissé de la nourriture, en pour cent ; null si aucun repas observé. */
    static Integer share(List<PlateReturn> found) {
        if (found.isEmpty()) {
            return null;
        }
        long withLeftovers = found.stream().filter(r -> r.getLeftFood() > 0).count();
        return (int) Math.round(100.0 * withLeftovers / found.size());
    }

    private Restaurant restaurant(String code) {
        return restaurants.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
    }
}
