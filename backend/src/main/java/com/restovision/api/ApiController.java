package com.restovision.api;

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

    public ApiController(AuthService auth, RestaurantQueryService queries) {
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

    @GetMapping("/stats/today")
    public StatsDto stats(@AuthenticationPrincipal Jwt jwt) {
        return queries.statsToday(restaurantOf(jwt));
    }
}
