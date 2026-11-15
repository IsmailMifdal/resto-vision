package com.restovision.stock;

import java.time.Instant;
import java.util.List;

/**
 * V2 - Connexion à une caisse (Zelty, Lightspeed, SumUp, Square...).
 * Une implémentation par caisse. Les ventes servent à décrémenter le stock à partir des recettes.
 */
public interface PosConnector {
    record SaleLine(String productCode, int quantity, Instant soldAt) {}

    /** Ventes enregistrées par la caisse depuis `since`. */
    List<SaleLine> salesSince(String restaurantCode, Instant since);
}
