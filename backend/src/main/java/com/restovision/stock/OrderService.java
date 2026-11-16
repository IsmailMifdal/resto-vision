package com.restovision.stock;

import com.restovision.domain.Restaurant;
import com.restovision.repo.RestaurantRepository;
import com.restovision.stock.StockDtos.OrderDto;
import com.restovision.stock.StockDtos.OrderLineDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proposition de commande fournisseur.
 *
 * Pour chaque ingrédient : ce qu'il s'en consomme par jour d'après les ventes importées, puis la quantité
 * à commander pour tenir le nombre de jours voulu en restant au-dessus du seuil. C'est une proposition :
 * le gérant la relit et l'envoie lui-même.
 */
@Service
public class OrderService {
    /** Jours de ventes pris en compte pour estimer la consommation. */
    static final int WINDOW_DAYS = 28;
    private static final Set<String> COUNTED_UNITS = Set.of("piece", "pieces", "unite", "unites", "u", "pc", "pcs");

    private final RestaurantRepository restaurants;
    private final StockRepositories.Ingredients ingredients;
    private final StockRepositories.Dishes dishes;
    private final StockRepositories.Sales sales;
    private final ZoneId timezone;

    public OrderService(RestaurantRepository restaurants, StockRepositories.Ingredients ingredients,
                        StockRepositories.Dishes dishes, StockRepositories.Sales sales,
                        @Value("${restovision.timezone:Europe/Paris}") String timezone) {
        this.restaurants = restaurants;
        this.ingredients = ingredients;
        this.dishes = dishes;
        this.sales = sales;
        this.timezone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public OrderDto proposal(String restaurantCode, int coverDays) {
        Restaurant restaurant = restaurants.findByCode(restaurantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        int days = Math.max(1, Math.min(60, coverDays));
        List<Sale> recent = sales.findMatchedSince(restaurant, LocalDate.now(timezone).minusDays(WINDOW_DAYS));
        int salesDays = (int) recent.stream().map(Sale::getSaleDate).distinct().count();

        Map<String, Dish> byName = new HashMap<>();
        for (Dish dish : dishes.findByRestaurantOrderByNameAsc(restaurant)) {
            byName.put(SalesCsv.key(dish.getName()), dish);
        }
        Map<UUID, BigDecimal> used = new HashMap<>();
        for (Sale sale : recent) {
            Dish dish = byName.get(SalesCsv.key(sale.getDishName()));
            if (dish == null) {
                continue;  // la recette a été supprimée depuis l'import
            }
            for (RecipeLine line : dish.getLines()) {
                used.merge(line.getIngredient().getId(), line.getQuantity().multiply(sale.getQuantity()), BigDecimal::add);
            }
        }

        List<OrderLineDto> lines = new ArrayList<>();
        for (Ingredient ingredient : ingredients.findByRestaurantOrderByNameAsc(restaurant)) {
            OrderLineDto line = line(ingredient, used.get(ingredient.getId()), salesDays, days);
            if (line.toOrder().signum() > 0) {
                lines.add(line);
            }
        }
        lines.sort(Comparator.comparing((OrderLineDto line) -> line.supplier() == null ? "\uFFFF" : line.supplier().toLowerCase())
                .thenComparing(OrderLineDto::ingredient));
        return new OrderDto(days, salesDays, lines);
    }

    static OrderLineDto line(Ingredient ingredient, BigDecimal usedInWindow, int salesDays, int coverDays) {
        BigDecimal quantity = ingredient.getQuantity();
        BigDecimal dailyUse = usedInWindow == null || salesDays == 0 ? null
                : usedInWindow.divide(BigDecimal.valueOf(salesDays), 3, RoundingMode.HALF_UP);
        BigDecimal target;
        BigDecimal daysLeft = null;
        if (dailyUse != null && dailyUse.signum() > 0) {
            // Tenir `coverDays` jours et finir encore au-dessus du seuil.
            target = dailyUse.multiply(BigDecimal.valueOf(coverDays)).add(ingredient.getThreshold());
            daysLeft = quantity.max(BigDecimal.ZERO).divide(dailyUse, 1, RoundingMode.DOWN);
        } else {
            // Pas de ventes connues pour cet ingrédient : on ne propose une commande que s'il est sous son seuil.
            target = ingredient.isLow() ? ingredient.getThreshold().multiply(BigDecimal.valueOf(2)) : quantity;
        }
        BigDecimal toOrder = round(target.subtract(quantity).max(BigDecimal.ZERO), ingredient.getUnit());
        return new OrderLineDto(ingredient.getName(), ingredient.getUnit(), ingredient.getSupplier(), quantity,
                dailyUse, daysLeft, toOrder);
    }

    /** Arrondi vers le haut : à l'unité pour ce qui se compte, au dixième sinon. */
    static BigDecimal round(BigDecimal value, String unit) {
        boolean counted = COUNTED_UNITS.contains(SalesCsv.key(unit).replace(".", ""));
        return value.setScale(counted ? 0 : 1, RoundingMode.CEILING);
    }
}
