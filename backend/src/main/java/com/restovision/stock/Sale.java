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
import java.time.LocalDate;
import java.util.UUID;

/** Une ligne de vente lue dans un fichier de la caisse. */
@Entity
@Table(name = "rv_sale")
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SalesImport salesImport;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "dish_name", nullable = false, length = 120)
    private String dishName;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /** Faux si aucun plat de la carte ne porte ce nom : la vente n'a rien retiré du stock. */
    @Column(nullable = false)
    private boolean matched;

    protected Sale() {}

    public Sale(SalesImport salesImport, LocalDate saleDate, String dishName, BigDecimal quantity, boolean matched) {
        this.salesImport = salesImport;
        this.saleDate = saleDate;
        this.dishName = dishName.length() > 120 ? dishName.substring(0, 120) : dishName;
        this.quantity = quantity;
        this.matched = matched;
    }
}
