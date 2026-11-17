package com.restovision.api;

import com.restovision.api.Dtos.WasteDayDto;
import com.restovision.waste.WasteService;
import com.restovision.api.Dtos.BoxDto;
import com.restovision.edge.BoxService;
import com.restovision.api.Dtos.ForecastDto;
import com.restovision.api.Dtos.ReportDto;
import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.DaySummaryDto;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import com.restovision.api.Dtos.AlertDto;
import com.restovision.api.Dtos.AlertRuleDto;
import com.restovision.api.Dtos.AlertRuleUpdate;
import com.restovision.api.Dtos.LoginRequest;
import com.restovision.api.Dtos.LoginResponse;
import com.restovision.api.Dtos.StatsDto;
import com.restovision.api.Dtos.ZoneDto;
import com.restovision.security.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de la V1. Le restaurant vient toujours du jeton, jamais de l'URL. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth;
    private final RestaurantQueryService queries;
    private final BoxService boxes;
    private final WasteService waste;

    public ApiController(AuthService auth, RestaurantQueryService queries, BoxService boxes, WasteService waste) {
        this.boxes = boxes;
        this.waste = waste;
        this.auth = auth;
        this.queries = queries;
    }

    private static String restaurantOf(Jwt jwt) {
        return jwt.getClaimAsString("restaurant");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/zones")
    public List<ZoneDto> zones(@AuthenticationPrincipal Jwt jwt) {
        return queries.zones(restaurantOf(jwt));
    }

    @GetMapping("/alerts")
    public List<AlertDto> alerts(@AuthenticationPrincipal Jwt jwt) {
        return queries.openAlerts(restaurantOf(jwt));
    }

    @PostMapping("/alerts/{id}/ack")
    public AlertDto acknowledge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return queries.acknowledge(restaurantOf(jwt), id);
    }

    @GetMapping("/alert-rules")
    public List<AlertRuleDto> rules(@AuthenticationPrincipal Jwt jwt) {
        return queries.rules(restaurantOf(jwt));
    }

    @PutMapping("/alert-rules/{id}")
    public AlertRuleDto updateRule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody AlertRuleUpdate update) {
        return queries.updateRule(restaurantOf(jwt), id, update);
    }

    /** Une journée de service table par table. Sans date : aujourd'hui. */
    @GetMapping("/stats/day")
    public DayDto day(@AuthenticationPrincipal Jwt jwt,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queries.day(restaurantOf(jwt), date);
    }

    /** Résumé des derniers jours, du plus ancien au plus récent. */
    @GetMapping("/stats/days")
    public List<DaySummaryDto> days(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "7") int count) {
        return queries.lastDays(restaurantOf(jwt), count);
    }

    /** Le rapport du jour en phrases. Sans date : aujourd'hui. */
    @GetMapping("/reports/daily")
    public ReportDto dailyReport(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queries.dailyReport(restaurantOf(jwt), date, false);
    }

    /** Faire rédiger à nouveau le rapport d'un jour. Réservé au gérant. */
    @PostMapping("/reports/daily")
    public ReportDto rewriteDailyReport(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queries.dailyReport(restaurantOf(jwt), date, true);
    }

    /** Affluence attendue heure par heure. Sans date : demain. */
    @GetMapping("/forecast")
    public ForecastDto forecast(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queries.forecast(restaurantOf(jwt), date);
    }

    /** Ce qui est revenu en cuisine un jour donné. Sans date : aujourd'hui. */
    @GetMapping("/waste")
    public WasteDayDto waste(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return waste.day(restaurantOf(jwt), date);
    }

    /** Les boîtiers du restaurant et leur dernier signal de vie. */
    @GetMapping("/boxes")
    public List<BoxDto> boxes(@AuthenticationPrincipal Jwt jwt) {
        return boxes.list(restaurantOf(jwt));
    }

    @GetMapping("/stats/today")
    public StatsDto stats(@AuthenticationPrincipal Jwt jwt) {
        return queries.statsToday(restaurantOf(jwt));
    }
}
