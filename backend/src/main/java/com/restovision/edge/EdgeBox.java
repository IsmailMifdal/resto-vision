package com.restovision.edge;

import com.restovision.domain.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Un boîtier d'analyse vidéo et son dernier signal de vie. */
@Entity
@Table(name = "rv_edge_box", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "box_id"}))
public class EdgeBox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(name = "box_id", nullable = false, length = 120)
    private String boxId;

    @Column(length = 40)
    private String source;

    @Column(length = 40)
    private String version;

    private Double fps;

    @Column(nullable = false)
    private int buffered;

    @Column(name = "uptime_seconds", nullable = false)
    private long uptimeSeconds;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(nullable = false)
    private boolean online;

    @Column(name = "update_error", length = 200)
    private String updateError;

    protected EdgeBox() {}

    public EdgeBox(Restaurant restaurant, String boxId) {
        this.restaurant = restaurant;
        this.boxId = boxId;
    }

    public void heard(String source, String version, Double fps, int buffered, long uptimeSeconds, Instant at,
                      String updateError) {
        this.updateError = cut(updateError, 200);
        this.source = cut(source, 40);
        this.version = cut(version, 40);
        this.fps = fps;
        this.buffered = buffered;
        this.uptimeSeconds = uptimeSeconds;
        this.lastSeen = at.truncatedTo(ChronoUnit.MICROS);
        this.online = true;
    }

    public void markOffline() {
        this.online = false;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Restaurant getRestaurant() { return restaurant; }
    public String getBoxId() { return boxId; }
    public String getSource() { return source; }
    public String getVersion() { return version; }
    public Double getFps() { return fps; }
    public int getBuffered() { return buffered; }
    public long getUptimeSeconds() { return uptimeSeconds; }
    public Instant getLastSeen() { return lastSeen; }
    public boolean isOnline() { return online; }
    public String getUpdateError() { return updateError; }
}
