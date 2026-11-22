package com.restovision.report;

/** Met les chiffres d'une journée en phrases, pour le gérant. */
public interface ReportWriter {
    String write(ReportFacts facts);
}
