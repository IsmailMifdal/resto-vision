package com.restovision.waste;

import com.restovision.domain.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Une table au départ des clients : combien d'aliments ont été vus pendant le repas, combien restent. */
@Entity
@Table(name = "rv_plate_return")
public class PlateReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Zone zone;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "served_food", nullable = false)
    private int servedFood;

    @Column(name = "left_food", nullable = false)
    private int leftFood;

    protected PlateReturn() {}

    public PlateReturn(Zone zone, Instant occurredAt, int servedFood, int leftFood) {
        this.zone = zone;
        this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        this.servedFood = Math.max(0, servedFood);
        this.leftFood = Math.max(0, Math.min(leftFood, this.servedFood));
    }

    public Zone getZone() { return zone; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getServedFood() { return servedFood; }
    public int getLeftFood() { return leftFood; }
}
