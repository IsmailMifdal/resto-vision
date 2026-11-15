package com.restovision.forecast;

import java.time.LocalDate;
import java.util.Map;

/**
 * V2 - Prédiction d'affluence à partir de l'historique (rv_zone_event), de la météo et du jour.
 * Sert aussi à préparer le bon de commande fournisseur quand le stock prévu passe sous un seuil.
 */
public interface ForecastService {
    /** Nombre de clients attendus par heure (0-23) pour une journée. */
    Map<Integer, Integer> expectedCustomersByHour(String restaurantCode, LocalDate day);
}
