package com.restovision.api;

import com.restovision.report.DailyReport;
import java.time.LocalDate;
import java.util.List;
import com.restovision.domain.Alert;
import com.restovision.domain.AlertRule;
import com.restovision.domain.AlertType;
import com.restovision.domain.TableState;
import com.restovision.domain.Zone;
import com.restovision.domain.ZoneType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/** Objets échangés avec l'application web. Les entités JPA ne sortent jamais de l'API. */
public final class Dtos {
    private Dtos() {}

    public record ZoneDto(String code, ZoneType type, TableState state, Instant stateSince,
                          int persons, int dishes, Integer estimatedWaitSeconds, List<List<Double>> polygon) {
        public static ZoneDto of(Zone z) {
            return new ZoneDto(z.getCode(), z.getType(), z.getState(), z.getStateSince(),
                    z.getPersons(), z.getDishes(), z.getEstimatedWaitSeconds(), z.getPolygon());
        }
    }

    public record AlertDto(UUID id, AlertType type, String zoneCode, String message,
                           Instant createdAt, Instant acknowledgedAt, Instant resolvedAt) {
        public static AlertDto of(Alert a) {
            return new AlertDto(a.getId(), a.getType(), a.getZone().getCode(), a.getMessage(),
                    a.getCreatedAt(), a.getAcknowledgedAt(), a.getResolvedAt());
        }
    }

    public record AlertRuleDto(UUID id, AlertType type, int threshold, boolean enabled) {
        public static AlertRuleDto of(AlertRule r) {
            return new AlertRuleDto(r.getId(), r.getType(), r.getThreshold(), r.isEnabled());
        }
    }

    public record AlertRuleUpdate(@Min(1) int threshold, boolean enabled) {}

    public record StatsDto(int tablesTotal, int tablesOccupied, int tablesToClear, double occupancyRate,
                           Integer averageWaitSeconds, int seatingsToday, int queuePersons, int queueWaitSeconds) {}

    /** Un état tenu par une table, de `start` à `end`. Les périodes « libre » ne sont pas listées. */
    public record SegmentDto(TableState state, Instant start, Instant end) {}

    public record TableTimelineDto(String code, List<SegmentDto> segments) {}

    /** Une heure de service. `hour` est l'heure locale du restaurant (12 = de 12 h à 13 h). */
    public record HourDto(int hour, double occupancyRate, Integer averageWaitSeconds, int seatings) {}

    /** Une journée de service. `from` est minuit, heure du restaurant ; `to` s'arrête à maintenant pour aujourd'hui. */
    public record DayDto(LocalDate date, Instant from, Instant to, int tablesTotal, List<TableTimelineDto> tables,
                         List<HourDto> hours, Integer averageWaitSeconds, Integer longestWaitSeconds,
                         String longestWaitTable, Instant longestWaitAt, int seatings,
                         Integer averageToClearSeconds, double peakOccupancyRate) {}

    public record DaySummaryDto(LocalDate date, Integer averageWaitSeconds, Integer longestWaitSeconds, int seatings,
                                double peakOccupancyRate, Integer averageToClearSeconds) {}

    /** Le rapport d'une journée. `partial` : journée en cours. `aiAvailable` : une clé d'API est configurée. */
    public record ReportDto(LocalDate date, String text, DailyReport.Source source, Instant generatedAt,
                            boolean partial, boolean aiAvailable) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record LoginResponse(String token, String restaurantCode, String restaurantName, String role) {}
}
