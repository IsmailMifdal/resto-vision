package com.restovision.stock;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Un ingrédient en stock, dans son unité (kg, L, pièce...). */
@Entity
@Table(name = "rv_ingredient", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "name"}))
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /** En dessous de cette quantité, l'ingrédient est signalé « à commander ». */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal threshold;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Chez qui on le commande. Facultatif. */
    @Column(length = 120)
    private String supplier;

    protected Ingredient() {}

    public Ingredient(Restaurant restaurant, String name, String unit, BigDecimal quantity, BigDecimal threshold) {
        this.restaurant = restaurant;
        update(name, unit, quantity, threshold);
    }

    public void update(String name, String unit, BigDecimal quantity, BigDecimal threshold) {
        this.name = name.strip();
        this.unit = unit.strip();
        this.quantity = quantity;
        this.threshold = threshold;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Retire ce qui a été vendu. Le stock peut passer sous zéro : c'est le signe d'un inventaire à refaire. */
    public void consume(BigDecimal used) {
        this.quantity = this.quantity.subtract(used);
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isLow() {
        return quantity.compareTo(threshold) <= 0;
    }

    public UUID getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getThreshold() { return threshold; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getSupplier() { return supplier; }

    public void setSupplier(String supplier) {
        this.supplier = supplier == null || supplier.isBlank() ? null : supplier.strip();
    }
}
