package com.restovision.report;

import com.restovision.domain.Restaurant;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Le rapport d'une journée terminée, gardé pour ne pas le faire rédiger à chaque lecture. */
@Entity
@Table(name = "rv_daily_report", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "report_date"}))
public class DailyReport {
    /** Qui a rédigé le texte : un modèle de langage, ou les phrases toutes faites du service. */
    public enum Source { AI, TEMPLATE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(nullable = false)
    private Instant generatedAt;

    protected DailyReport() {}

    public DailyReport(Restaurant restaurant, LocalDate reportDate) {
        this.restaurant = restaurant;
        this.reportDate = reportDate;
    }

    public void update(String text, Source source, Instant generatedAt) {
        this.text = text.length() > 4000 ? text.substring(0, 4000) : text;
        this.source = source;
        // La base garde les microsecondes : on arrondit ici pour relire exactement la même valeur.
        this.generatedAt = generatedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public LocalDate getReportDate() { return reportDate; }
    public String getText() { return text; }
    public Source getSource() { return source; }
    public Instant getGeneratedAt() { return generatedAt; }
}
