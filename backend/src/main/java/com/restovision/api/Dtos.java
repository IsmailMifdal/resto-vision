package com.restovision.api;

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
                          int persons, int dishes, Integer estimatedWaitSeconds) {
        public static ZoneDto of(Zone z) {
            return new ZoneDto(z.getCode(), z.getType(), z.getState(), z.getStateSince(),
                    z.getPersons(), z.getDishes(), z.getEstimatedWaitSeconds());
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

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record LoginResponse(String token, String restaurantCode, String restaurantName, String role) {}
}
