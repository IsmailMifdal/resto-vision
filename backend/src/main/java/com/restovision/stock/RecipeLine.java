package com.restovision.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Ce qu'un plat consomme d'un ingrédient, dans l'unité de l'ingrédient. */
@Entity
@Table(name = "rv_recipe_line")
public class RecipeLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Dish dish;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    protected RecipeLine() {}

    public RecipeLine(Dish dish, Ingredient ingredient, BigDecimal quantity) {
        this.dish = dish;
        this.ingredient = ingredient;
        this.quantity = quantity;
    }

    public Ingredient getIngredient() { return ingredient; }
    public BigDecimal getQuantity() { return quantity; }
}
