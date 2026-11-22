package com.restovision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "rv_restaurant")
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identifiant court envoyé par le boîtier (restaurantId). */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    /** V3 multi-sites : les restaurants d'une même chaîne partagent ce code. */
    private String groupCode;

    /** Position du restaurant, pour la météo. Nulle tant que le gérant ne l'a pas saisie. */
    private Double latitude;
    private Double longitude;

    protected Restaurant() {}

    public Restaurant(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    public void setLocation(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
