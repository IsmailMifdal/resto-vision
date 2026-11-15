package com.restovision.report;

import java.time.LocalDate;

/**
 * V2 - Rapport quotidien en langage naturel.
 * Principe : calculer les chiffres du jour ici (StatsService), puis demander à un modèle de langage
 * de les rédiger. Le modèle ne doit jamais inventer de chiffres : il ne reçoit que ceux calculés.
 */
public interface DailyReportService {
    String generate(String restaurantCode, LocalDate day);
}
