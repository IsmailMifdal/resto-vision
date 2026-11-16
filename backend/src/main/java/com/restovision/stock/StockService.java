package com.restovision.stock;

import com.restovision.domain.Restaurant;
import com.restovision.repo.RestaurantRepository;
import com.restovision.stock.StockDtos.DishDto;
import com.restovision.stock.StockDtos.DishRequest;
import com.restovision.stock.StockDtos.ImportResultDto;
import com.restovision.stock.StockDtos.IngredientDto;
import com.restovision.stock.StockDtos.IngredientRequest;
import com.restovision.stock.StockDtos.LastImportDto;
import com.restovision.stock.StockDtos.ProblemDto;
import com.restovision.stock.StockDtos.RecipeLineRequest;
import com.restovision.stock.StockDtos.StockDto;
import com.restovision.stock.StockDtos.UnknownDishDto;
import com.restovision.stock.StockDtos.UsageDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stock du restaurant. Les ventes viennent de la caisse (un fichier CSV), pas de la caméra :
 * chaque plat vendu retire du stock ce que dit sa recette.
 */
@Service
public class StockService {
    private final RestaurantRepository restaurants;
    private final StockRepositories.Ingredients ingredients;
    private final StockRepositories.Dishes dishes;
    private final StockRepositories.RecipeLines recipeLines;
    private final StockRepositories.Imports imports;
    private final StockRepositories.Sales sales;

    public StockService(RestaurantRepository restaurants, StockRepositories.Ingredients ingredients,
                        StockRepositories.Dishes dishes, StockRepositories.RecipeLines recipeLines,
                        StockRepositories.Imports imports, StockRepositories.Sales sales) {
        this.restaurants = restaurants;
        this.ingredients = ingredients;
        this.dishes = dishes;
        this.recipeLines = recipeLines;
        this.imports = imports;
        this.sales = sales;
    }

    private Restaurant restaurant(String code) {
        return restaurants.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
    }

    @Transactional(readOnly = true)
    public StockDto overview(String restaurantCode) {
        Restaurant restaurant = restaurant(restaurantCode);
        return new StockDto(
                ingredients.findByRestaurantOrderByNameAsc(restaurant).stream().map(IngredientDto::of).toList(),
                dishes.findByRestaurantOrderByNameAsc(restaurant).stream().map(DishDto::of).toList(),
                imports.findFirstByRestaurantOrderByImportedAtDesc(restaurant)
                        .map(last -> new LastImportDto(last.getFileName(), last.getImportedAt(), last.getLineCount()))
                        .orElse(null));
    }

    @Transactional
    public IngredientDto saveIngredient(String restaurantCode, UUID id, IngredientRequest request) {
        Restaurant restaurant = restaurant(restaurantCode);
        boolean taken = ingredients.findByRestaurantOrderByNameAsc(restaurant).stream()
                .anyMatch(other -> !other.getId().equals(id) && SalesCsv.key(other.getName()).equals(SalesCsv.key(request.name())));
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un ingrédient porte déjà ce nom.");
        }
        Ingredient ingredient;
        if (id == null) {
            ingredient = new Ingredient(restaurant, request.name(), request.unit(), request.quantity(), request.threshold());
        } else {
            ingredient = ownIngredient(restaurant, id);
            ingredient.update(request.name(), request.unit(), request.quantity(), request.threshold());
        }
        return IngredientDto.of(ingredients.save(ingredient));
    }

    @Transactional
    public void deleteIngredient(String restaurantCode, UUID id) {
        Ingredient ingredient = ownIngredient(restaurant(restaurantCode), id);
        if (recipeLines.existsByIngredient(ingredient)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet ingrédient est utilisé dans une recette. Retirez-le d'abord de la recette.");
        }
        ingredients.delete(ingredient);
    }

    @Transactional
    public DishDto saveDish(String restaurantCode, UUID id, DishRequest request) {
        Restaurant restaurant = restaurant(restaurantCode);
        boolean taken = dishes.findByRestaurantOrderByNameAsc(restaurant).stream()
                .anyMatch(other -> !other.getId().equals(id) && SalesCsv.key(other.getName()).equals(SalesCsv.key(request.name())));
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un plat porte déjà ce nom.");
        }
        Dish dish;
        if (id == null) {
            dish = new Dish(restaurant, request.name());
        } else {
            dish = ownDish(restaurant, id);
            dish.rename(request.name());
            dish.getLines().clear();
        }
        for (RecipeLineRequest line : request.lines()) {
            dish.getLines().add(new RecipeLine(dish, ownIngredient(restaurant, line.ingredientId()), line.quantity()));
        }
        return DishDto.of(dishes.save(dish));
    }

    @Transactional
    public void deleteDish(String restaurantCode, UUID id) {
        dishes.delete(ownDish(restaurant(restaurantCode), id));
    }

    /** Compte un fichier de ventes : chaque plat connu retire sa recette du stock. Un fichier déjà compté est refusé. */
    @Transactional
    public ImportResultDto importSales(String restaurantCode, String fileName, byte[] content) {
        Restaurant restaurant = restaurant(restaurantCode);
        String hash = sha256(content);
        if (imports.existsByRestaurantAndFileHash(restaurant, hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce fichier a déjà été importé : ses ventes sont déjà retirées du stock.");
        }
        SalesCsv.Parsed parsed;
        try {
            parsed = SalesCsv.parse(new String(content, StandardCharsets.UTF_8));
        } catch (SalesCsv.UnreadableFile e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (parsed.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune vente lisible dans ce fichier.");
        }

        Map<String, Dish> byName = new HashMap<>();
        for (Dish dish : dishes.findByRestaurantOrderByNameAsc(restaurant)) {
            byName.put(SalesCsv.key(dish.getName()), dish);
        }
        SalesImport record = imports.save(new SalesImport(restaurant, hash, fileName == null ? "ventes.csv" : fileName,
                parsed.lines().size()));
        Map<Ingredient, BigDecimal> used = new LinkedHashMap<>();
        Map<String, BigDecimal> unknown = new LinkedHashMap<>();
        BigDecimal sold = BigDecimal.ZERO;
        List<Sale> rows = new ArrayList<>();
        for (SalesCsv.Line line : parsed.lines()) {
            Dish dish = byName.get(SalesCsv.key(line.dish()));
            rows.add(new Sale(record, line.date(), line.dish(), line.quantity(), dish != null));
            sold = sold.add(line.quantity());
            if (dish == null) {
                unknown.merge(line.dish(), line.quantity(), BigDecimal::add);
                continue;
            }
            for (RecipeLine recipe : dish.getLines()) {
                used.merge(recipe.getIngredient(), recipe.getQuantity().multiply(line.quantity()), BigDecimal::add);
            }
        }
        sales.saveAll(rows);
        used.forEach(Ingredient::consume);

        return new ImportResultDto(record.getFileName(), parsed.lines().size(), sold,
                used.entrySet().stream().map(entry -> new UsageDto(entry.getKey().getName(), entry.getKey().getUnit(),
                        entry.getValue(), entry.getKey().getQuantity(), entry.getKey().isLow())).toList(),
                unknown.entrySet().stream().map(entry -> new UnknownDishDto(entry.getKey(), entry.getValue())).toList(),
                parsed.problems().stream().map(problem -> new ProblemDto(problem.line(), problem.message())).toList());
    }

    private Ingredient ownIngredient(Restaurant restaurant, UUID id) {
        return ingredients.findById(id).filter(i -> i.getRestaurant().getId().equals(restaurant.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingrédient introuvable"));
    }

    private Dish ownDish(Restaurant restaurant, UUID id) {
        return dishes.findById(id).filter(d -> d.getRestaurant().getId().equals(restaurant.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plat introuvable"));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
