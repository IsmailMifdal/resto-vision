package com.restovision.report;

import com.restovision.api.Dtos.DayDto;
import com.restovision.api.Dtos.DaySummaryDto;
import com.restovision.api.Dtos.ReportDto;
import com.restovision.domain.Restaurant;
import com.restovision.stats.HistoryService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rapport quotidien en langage naturel. Les chiffres sont calculés ici ; le rédacteur les met en phrases.
 * Une journée terminée est rédigée une fois puis gardée. La journée en cours est toujours rédigée sans IA,
 * à chaque lecture, puisque ses chiffres bougent encore.
 */
@Service
public class DailyReportService {
    private static final Logger log = LoggerFactory.getLogger(DailyReportService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private final HistoryService history;
    private final DailyReportRepository reports;
    private final TemplateReportWriter template;
    private final ClaudeReportWriter claude;
    private final ZoneId timezone;

    public DailyReportService(HistoryService history, DailyReportRepository reports, TemplateReportWriter template,
                              ClaudeReportWriter claude,
                              @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.history = history;
        this.reports = reports;
        this.template = template;
        this.claude = claude;
        this.timezone = ZoneId.of(timezone);
    }

    /** `regenerate` : rédiger à nouveau même si un rapport existe déjà pour ce jour. */
    @Transactional
    public ReportDto report(Restaurant restaurant, LocalDate date, boolean regenerate) {
        LocalDate today = LocalDate.now(timezone);
        LocalDate day = date == null ? today : date;
        boolean inProgress = !day.isBefore(today);
        ReportFacts facts = facts(restaurant, day, inProgress);
        if (inProgress) {
            return new ReportDto(day, template.write(facts), DailyReport.Source.TEMPLATE, Instant.now(), true,
                    claude.isConfigured());
        }
        DailyReport saved = reports.findByRestaurantAndReportDate(restaurant, day).orElse(null);
        if (saved == null || regenerate) {
            DailyReport report = saved == null ? new DailyReport(restaurant, day) : saved;
            write(report, facts);
            saved = reports.save(report);
        }
        return new ReportDto(day, saved.getText(), saved.getSource(), saved.getGeneratedAt(), false,
                claude.isConfigured());
    }

    private void write(DailyReport report, ReportFacts facts) {
        if (claude.isConfigured() && facts.hasService()) {
            try {
                report.update(claude.write(facts), DailyReport.Source.AI, Instant.now());
                return;
            } catch (RuntimeException e) {
                // Clé refusée, quota, réseau, réponse refusée : le gérant reçoit quand même son rapport.
                log.warn("Rapport par IA indisponible, phrases toutes faites utilisées : {}", e.getMessage());
            }
        }
        report.update(template.write(facts), DailyReport.Source.TEMPLATE, Instant.now());
    }

    ReportFacts facts(Restaurant restaurant, LocalDate day, boolean inProgress) {
        DayDto d = history.day(restaurant, day);
        List<Integer> otherDays = history.lastDays(restaurant, 7).stream()
                .filter(summary -> !summary.date().equals(day))
                .map(DaySummaryDto::averageWaitSeconds).filter(Objects::nonNull).toList();
        Integer weekAverage = otherDays.isEmpty() ? null
                : (int) otherDays.stream().mapToInt(Integer::intValue).average().orElse(0);
        String longestAt = d.longestWaitAt() == null ? null : clock(Duration.between(d.from(), d.longestWaitAt()));
        String date = day.format(DATE);
        return new ReportFacts(restaurant.getName(), date.substring(0, 1).toUpperCase(Locale.FRENCH) + date.substring(1),
                inProgress, d.tablesTotal(), d.seatings(), d.averageWaitSeconds(), weekAverage, d.longestWaitSeconds(),
                d.longestWaitTable(), longestAt, d.averageToClearSeconds(), (int) Math.round(d.peakOccupancyRate() * 100),
                d.hours().stream().map(hour -> new ReportFacts.Hour(hour.hour(),
                        (int) Math.round(hour.occupancyRate() * 100), hour.averageWaitSeconds(), hour.seatings())).toList());
    }

    private static String clock(Duration sinceMidnight) {
        return String.format("%02d:%02d", sinceMidnight.toHours() % 24, sinceMidnight.toMinutesPart());
    }
}
