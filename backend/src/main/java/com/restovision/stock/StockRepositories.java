package com.restovision.stock;

import com.restovision.domain.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accès aux tables du stock. */
public final class StockRepositories {
    private StockRepositories() {}

    public interface Ingredients extends JpaRepository<Ingredient, UUID> {
        List<Ingredient> findByRestaurantOrderByNameAsc(Restaurant restaurant);
        long countByRestaurant(Restaurant restaurant);
    }

    public interface Dishes extends JpaRepository<Dish, UUID> {
        List<Dish> findByRestaurantOrderByNameAsc(Restaurant restaurant);
    }

    public interface RecipeLines extends JpaRepository<RecipeLine, UUID> {
        boolean existsByIngredient(Ingredient ingredient);
    }

    public interface Imports extends JpaRepository<SalesImport, UUID> {
        boolean existsByRestaurantAndFileHash(Restaurant restaurant, String fileHash);
        Optional<SalesImport> findFirstByRestaurantOrderByImportedAtDesc(Restaurant restaurant);
    }

    public interface Sales extends JpaRepository<Sale, UUID> {}
}
