package com.restovision.report;

import java.util.List;

/**
 * Les chiffres d'une journée, déjà calculés. C'est tout ce que reçoit le rédacteur :
 * il met ces chiffres en phrases, il n'en invente aucun.
 */
public record ReportFacts(String restaurant, String date, boolean dayInProgress, int tables, int tablesSeated,
                          Integer averageWaitSeconds, Integer weekAverageWaitSeconds, Integer longestWaitSeconds,
                          String longestWaitTable, String longestWaitTime, Integer averageClearingSeconds,
                          int peakOccupancyPercent, List<Hour> hours) {

    /** Une heure de service : 12 = de 12 h à 13 h. */
    public record Hour(int hour, int occupancyPercent, Integer averageWaitSeconds, int tablesSeated) {}

    public boolean hasService() {
        return !hours.isEmpty();
    }

    /** « 9 min 28 », « 45 s ». */
    public static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        int rest = seconds % 60;
        return rest == 0 ? (seconds / 60) + " min" : (seconds / 60) + " min " + String.format("%02d", rest);
    }
}
