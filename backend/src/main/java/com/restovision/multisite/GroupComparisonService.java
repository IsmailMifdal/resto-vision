package com.restovision.multisite;

import java.util.List;

/** V3 - Classement des restaurants d'un même groupe (Restaurant.groupCode). */
public interface GroupComparisonService {
    record SiteScore(String restaurantCode, Integer averageWaitSeconds, double occupancyRate) {}

    List<SiteScore> compare(String groupCode);
}
