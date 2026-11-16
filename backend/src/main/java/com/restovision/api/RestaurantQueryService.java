package com.restovision.api;

import com.restovision.api.Dtos.ForecastDto;
import com.restovision.forecast.ForecastService;
import com.restovision.api.Dtos.ReportDto;
import com.restovision.report.DailyReportService;
import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.DaySummaryDto;
import com.restovision.stats.HistoryService;
import java.time.LocalDate;
import com.restovision.api.Dtos.AlertDto;
import com.restovision.api.Dtos.AlertRuleDto;
import com.restovision.api.Dtos.AlertRuleUpdate;
import com.restovision.api.Dtos.StatsDto;
import com.restovision.api.Dtos.ZoneDto;
import com.restovision.domain.Alert;
import com.restovision.domain.AlertRule;
import com.restovision.domain.Restaurant;
import com.restovision.live.LiveNotifier;
import com.restovision.repo.AlertRepository;
import com.restovision.repo.AlertRuleRepository;
import com.restovision.repo.RestaurantRepository;
import com.restovision.repo.ZoneRepository;
import com.restovision.stats.StatsService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Lectures et petites modifications pour l'application web, toujours limitées au restaurant de l'utilisateur. */
@Service
public class RestaurantQueryService {
    private final RestaurantRepository restaurants;
    private final ZoneRepository zones;
    private final AlertRepository alerts;
    private final AlertRuleRepository rules;
    private final StatsService stats;
    private final LiveNotifier notifier;
    private final HistoryService history;
    private final DailyReportService dailyReports;
    private final ForecastService forecasts;

    public RestaurantQueryService(RestaurantRepository restaurants, ZoneRepository zones, AlertRepository alerts,
                                  AlertRuleRepository rules, StatsService stats, LiveNotifier notifier,
                                  HistoryService history, DailyReportService dailyReports,
                                  ForecastService forecasts) {
        this.restaurants = restaurants;
        this.zones = zones;
        this.alerts = alerts;
        this.rules = rules;
        this.stats = stats;
        this.notifier = notifier;
        this.history = history;
        this.dailyReports = dailyReports;
        this.forecasts = forecasts;
    }

    private Restaurant restaurant(String code) {
        return restaurants.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
    }

    @Transactional(readOnly = true)
    public List<ZoneDto> zones(String restaurantCode) {
        return zones.findByRestaurantOrderByCodeAsc(restaurant(restaurantCode)).stream().map(ZoneDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> openAlerts(String restaurantCode) {
        return alerts.findOpen(restaurant(restaurantCode)).stream().map(AlertDto::of).toList();
    }

    @Transactional
    public AlertDto acknowledge(String restaurantCode, UUID alertId) {
        Alert alert = alerts.findById(alertId)
                .filter(a -> a.getZone().getRestaurant().getCode().equals(restaurantCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte introuvable"));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(Instant.now());
        }
        AlertDto dto = AlertDto.of(alert);
        notifier.alert(restaurantCode, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleDto> rules(String restaurantCode) {
        return rules.findByRestaurantOrderByTypeAsc(restaurant(restaurantCode)).stream().map(AlertRuleDto::of).toList();
    }

    @Transactional
    public AlertRuleDto updateRule(String restaurantCode, UUID ruleId, AlertRuleUpdate update) {
        AlertRule rule = rules.findById(ruleId)
                .filter(r -> r.getRestaurant().getCode().equals(restaurantCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle introuvable"));
        rule.setThreshold(update.threshold());
        rule.setEnabled(update.enabled());
        return AlertRuleDto.of(rule);
    }

    @Transactional(readOnly = true)
    public DayDto day(String restaurantCode, LocalDate date) {
        return history.day(restaurant(restaurantCode), date);
    }

    @Transactional(readOnly = true)
    public List<DaySummaryDto> lastDays(String restaurantCode, int count) {
        return history.lastDays(restaurant(restaurantCode), Math.max(1, Math.min(31, count)));
    }

    @Transactional
    public ReportDto dailyReport(String restaurantCode, LocalDate date, boolean regenerate) {
        return dailyReports.report(restaurant(restaurantCode), date, regenerate);
    }

    @Transactional(readOnly = true)
    public ForecastDto forecast(String restaurantCode, LocalDate date) {
        return forecasts.forecast(restaurant(restaurantCode), date);
    }

    @Transactional(readOnly = true)
    public StatsDto statsToday(String restaurantCode) {
        return stats.today(restaurant(restaurantCode));
    }
}
