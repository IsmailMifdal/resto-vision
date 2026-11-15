package com.restovision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "rv_alert_rule")
public class AlertRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    /** Secondes pour UNSERVED et TO_CLEAR, nombre de personnes pour QUEUE_LENGTH. */
    @Column(nullable = false)
    private int threshold;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AlertRule() {}

    public AlertRule(Restaurant restaurant, AlertType type, int threshold) {
        this.restaurant = restaurant;
        this.type = type;
        this.threshold = threshold;
    }

    public UUID getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public AlertType getType() { return type; }
    public int getThreshold() { return threshold; }
    public void setThreshold(int threshold) { this.threshold = threshold; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
