package com.restovision.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Une table ou une file d'attente, avec son état courant. */
@Entity
@Table(name = "rv_zone", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "code"}))
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ZoneType type;

    @Enumerated(EnumType.STRING)
    private TableState state;

    private Instant stateSince;
    private int persons;
    private int dishes;
    private Integer estimatedWaitSeconds;
    private Instant updatedAt;

    /** Contour de la zone dans l'image de la caméra, en pixels : "x,y;x,y;…". Sert à dessiner le plan de salle. */
    @Column(length = 4000)
    private String polygon;

    protected Zone() {}

    public Zone(Restaurant restaurant, String code, ZoneType type) {
        this.restaurant = restaurant;
        this.code = code;
        this.type = type;
        if (type == ZoneType.TABLE) {
            this.state = TableState.EMPTY;
        }
    }

    public UUID getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getCode() { return code; }
    public ZoneType getType() { return type; }
    public TableState getState() { return state; }
    public void setState(TableState state) { this.state = state; }
    public Instant getStateSince() { return stateSince; }
    public void setStateSince(Instant stateSince) { this.stateSince = stateSince; }
    public int getPersons() { return persons; }
    public void setPersons(int persons) { this.persons = persons; }
    public int getDishes() { return dishes; }
    public void setDishes(int dishes) { this.dishes = dishes; }
    public Integer getEstimatedWaitSeconds() { return estimatedWaitSeconds; }
    public void setEstimatedWaitSeconds(Integer estimatedWaitSeconds) { this.estimatedWaitSeconds = estimatedWaitSeconds; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Liste de points [x, y], ou null si le boîtier n'a pas encore envoyé le plan. */
    public List<List<Double>> getPolygon() {
        if (polygon == null || polygon.isBlank()) {
            return null;
        }
        List<List<Double>> points = new ArrayList<>();
        for (String point : polygon.split(";")) {
            String[] xy = point.split(",");
            points.add(List.of(Double.valueOf(xy[0]), Double.valueOf(xy[1])));
        }
        return points;
    }

    public void setPolygon(List<List<Double>> points) {
        this.polygon = points.stream()
                .map(point -> Math.round(point.get(0)) + "," + Math.round(point.get(1)))
                .collect(Collectors.joining(";"));
    }
}
