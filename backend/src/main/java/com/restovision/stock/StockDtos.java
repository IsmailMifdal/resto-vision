package com.restovision.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Objets échangés avec l'application web pour le stock. */
public final class StockDtos {
    private StockDtos() {}

    public record IngredientDto(UUID id, String name, String unit, BigDecimal quantity, BigDecimal threshold,
                                boolean low, Instant updatedAt, String supplier) {
        public static IngredientDto of(Ingredient i) {
            return new IngredientDto(i.getId(), i.getName(), i.getUnit(), i.getQuantity(), i.getThreshold(), i.isLow(),
                    i.getUpdatedAt(), i.getSupplier());
        }
    }

    public record IngredientRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 20) String unit,
                                    @NotNull BigDecimal quantity, @NotNull @DecimalMin("0") BigDecimal threshold,
                                    @Size(max = 120) String supplier) {
        public IngredientRequest(String name, String unit, BigDecimal quantity, BigDecimal threshold) {
            this(name, unit, quantity, threshold, null);
        }
    }

    public record RecipeLineDto(UUID ingredientId, String ingredient, String unit, BigDecimal quantity) {}

    public record DishDto(UUID id, String name, List<RecipeLineDto> lines) {
        public static DishDto of(Dish dish) {
            return new DishDto(dish.getId(), dish.getName(), dish.getLines().stream()
                    .map(line -> new RecipeLineDto(line.getIngredient().getId(), line.getIngredient().getName(),
                            line.getIngredient().getUnit(), line.getQuantity())).toList());
        }
    }

    public record RecipeLineRequest(@NotNull UUID ingredientId, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) {}

    public record DishRequest(@NotBlank @Size(max = 120) String name, @NotNull @Valid List<RecipeLineRequest> lines) {}

    public record LastImportDto(String fileName, Instant importedAt, int lines) {}

    public record StockDto(List<IngredientDto> ingredients, List<DishDto> dishes, LastImportDto lastImport) {}

    /** Ce qu'un ingrédient a perdu pendant un import. */
    public record UsageDto(String ingredient, String unit, BigDecimal used, BigDecimal remaining, boolean low) {}

    /** Un plat vendu que la carte ne connaît pas : il n'a rien retiré du stock. */
    public record UnknownDishDto(String name, BigDecimal quantity) {}

    public record ProblemDto(int line, String message) {}

    /**
     * Une ligne du bon de commande. `dailyUse` et `daysLeft` sont nuls quand aucune vente importée
     * ne permet de les estimer.
     */
    public record OrderLineDto(String ingredient, String unit, String supplier, BigDecimal quantity,
                               BigDecimal dailyUse, BigDecimal daysLeft, BigDecimal toOrder) {}

    /** Proposition de commande pour tenir `coverDays` jours. `salesDays` : jours de ventes utilisés pour l'estimation. */
    public record OrderDto(int coverDays, int salesDays, List<OrderLineDto> lines) {}

    /** Bilan d'un import de ventes. */
    public record ImportResultDto(String fileName, int lines, BigDecimal dishesSold, List<UsageDto> usage,
                                  List<UnknownDishDto> unknownDishes, List<ProblemDto> problems) {}
}
