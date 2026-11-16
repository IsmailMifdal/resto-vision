package com.restovision.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateReportWriterTest {
    private final TemplateReportWriter writer = new TemplateReportWriter();

    @Test
    void aFullDayIsToldWithItsOwnFigures() {
        String text = writer.write(new ReportFacts("Chez Démo", "Samedi 14 novembre", false, 8, 31, 568, 347, 1012,
                "T4", "13:15", 306, 100, List.of(
                        new ReportFacts.Hour(12, 72, 540, 8),
                        new ReportFacts.Hour(13, 61, 690, 5),
                        new ReportFacts.Hour(20, 85, 600, 9))));

        assertThat(text).startsWith("Samedi 14 novembre : 31 tablées accueillies sur 8 tables");
        assertThat(text).contains("pic à 100 %", "20 h – 21 h (85 %)");
        assertThat(text).contains("en moyenne 9 min 28 avant d'être servis, contre 5 min 47 sur les 7 derniers jours");
        assertThat(text).contains("16 min 52, table T4 à 13:15");
        assertThat(text).contains("créneau le plus lent a été 13 h – 14 h : 11 min 30");
        assertThat(text).contains("5 min 06 à être débarrassée");
    }

    @Test
    void whatWasNotMeasuredIsLeftOutNotInvented() {
        String text = writer.write(new ReportFacts("Chez Démo", "Lundi 16 novembre", true, 8, 1, null, null, null,
                null, null, null, 13, List.of(new ReportFacts.Hour(12, 13, null, 1))));

        assertThat(text).startsWith("Lundi 16 novembre, journée en cours : 1 tablée accueillie sur 8 tables");
        assertThat(text).doesNotContain("attendu", "attente", "débarrassée", "null");
    }

    @Test
    void aDayWithoutServiceSaysSo() {
        assertThat(writer.write(new ReportFacts("Chez Démo", "Dimanche 15 novembre", false, 8, 0, null, null, null,
                null, null, null, 0, List.of()))).isEqualTo("Dimanche 15 novembre : aucun service enregistré.");
    }

    @Test
    void durationsReadLikeAClock() {
        assertThat(ReportFacts.duration(45)).isEqualTo("45 s");
        assertThat(ReportFacts.duration(300)).isEqualTo("5 min");
        assertThat(ReportFacts.duration(306)).isEqualTo("5 min 06");
    }
}
