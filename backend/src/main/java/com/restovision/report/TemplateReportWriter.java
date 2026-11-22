package com.restovision.report;

import java.util.Comparator;
import org.springframework.stereotype.Component;

/** Rédaction sans modèle de langage : des phrases fixes remplies avec les chiffres. Toujours disponible. */
@Component
public class TemplateReportWriter implements ReportWriter {
    @Override
    public String write(ReportFacts f) {
        if (!f.hasService()) {
            return f.date() + " : aucun service enregistré.";
        }
        StringBuilder text = new StringBuilder();
        text.append(f.date()).append(f.dayInProgress() ? ", journée en cours : " : " : ")
                .append(f.tablesSeated()).append(f.tablesSeated() > 1 ? " tablées accueillies" : " tablée accueillie")
                .append(" sur ").append(f.tables()).append(" tables");
        f.hours().stream().max(Comparator.comparingInt(ReportFacts.Hour::occupancyPercent)).ifPresent(busiest ->
                text.append(", avec un pic à ").append(f.peakOccupancyPercent()).append(" % d'occupation. L'heure la plus remplie a été ")
                        .append(slot(busiest.hour())).append(" (").append(busiest.occupancyPercent()).append(" %)"));
        text.append(".");

        if (f.averageWaitSeconds() != null) {
            text.append(" Les clients ont attendu en moyenne ").append(ReportFacts.duration(f.averageWaitSeconds()))
                    .append(" avant d'être servis");
            if (f.weekAverageWaitSeconds() != null) {
                text.append(", contre ").append(ReportFacts.duration(f.weekAverageWaitSeconds()))
                        .append(" sur les 7 derniers jours");
            }
            text.append(".");
        }
        if (f.longestWaitSeconds() != null) {
            text.append(" L'attente la plus longue a été de ").append(ReportFacts.duration(f.longestWaitSeconds()))
                    .append(", table ").append(f.longestWaitTable()).append(" à ").append(f.longestWaitTime()).append(".");
        }
        f.hours().stream().filter(hour -> hour.averageWaitSeconds() != null)
                .max(Comparator.comparingInt(ReportFacts.Hour::averageWaitSeconds))
                .filter(slowest -> f.averageWaitSeconds() != null && slowest.averageWaitSeconds() > f.averageWaitSeconds())
                .ifPresent(slowest -> text.append(" Le créneau le plus lent a été ").append(slot(slowest.hour()))
                        .append(" : ").append(ReportFacts.duration(slowest.averageWaitSeconds())).append(" d'attente en moyenne."));
        if (f.averageClearingSeconds() != null) {
            text.append(" Une table libérée a mis en moyenne ").append(ReportFacts.duration(f.averageClearingSeconds()))
                    .append(" à être débarrassée.");
        }
        return text.toString();
    }

    private static String slot(int hour) {
        return hour + " h – " + (hour + 1) + " h";
    }
}
