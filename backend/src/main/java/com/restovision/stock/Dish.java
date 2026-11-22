package com.restovision.stock;

import com.restovision.domain.Restaurant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un plat de la carte et sa recette. Son nom est celui que la caisse écrit dans ses exports. */
@Entity
@Table(name = "rv_dish", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "name"}))
public class Dish {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 120)
    private String name;

    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeLine> lines = new ArrayList<>();

    protected Dish() {}

    public Dish(Restaurant restaurant, String name) {
        this.restaurant = restaurant;
        this.name = name.strip();
    }

    public void rename(String name) {
        this.name = name.strip();
    }

    public UUID getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getName() { return name; }
    public List<RecipeLine> getLines() { return lines; }
}
