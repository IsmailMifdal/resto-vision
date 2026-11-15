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
import java.time.Instant;
import java.util.UUID;

/** Historique : une ligne par événement reçu du boîtier. Sert aux statistiques et, en V2, aux prédictions. */
@Entity
@Table(name = "rv_zone_event")
public class ZoneEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    private TableState state;

    @Enumerated(EnumType.STRING)
    private TableState previousState;

    private int persons;
    private int dishes;

    @Column(nullable = false)
    private Instant occurredAt;

    protected ZoneEvent() {}

    public ZoneEvent(Zone zone, TableState state, TableState previousState, int persons, int dishes, Instant occurredAt) {
        this.zone = zone;
        this.state = state;
        this.previousState = previousState;
        this.persons = persons;
        this.dishes = dishes;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public Zone getZone() { return zone; }
    public TableState getState() { return state; }
    public TableState getPreviousState() { return previousState; }
    public int getPersons() { return persons; }
    public int getDishes() { return dishes; }
    public Instant getOccurredAt() { return occurredAt; }
}
