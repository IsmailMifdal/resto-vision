package com.restovision.demo;

import com.restovision.domain.Restaurant;
import com.restovision.repo.RestaurantRepository;
import com.restovision.stock.Dish;
import com.restovision.stock.Ingredient;
import com.restovision.stock.RecipeLine;
import com.restovision.stock.StockRepositories;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Un petit stock et quatre recettes pour le restaurant de démonstration. Le fichier infra/ventes-exemple.csv va avec. */
@Component
@Order(2)
@ConditionalOnProperty(name = "restovision.demo.enabled", havingValue = "true")
public class DemoStockSeeder implements CommandLineRunner {
    private final RestaurantRepository restaurants;
    private final StockRepositories.Ingredients ingredients;
    private final StockRepositories.Dishes dishes;

    public DemoStockSeeder(RestaurantRepository restaurants, StockRepositories.Ingredients ingredients,
                           StockRepositories.Dishes dishes) {
        this.restaurants = restaurants;
        this.ingredients = ingredients;
        this.dishes = dishes;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Restaurant demo = restaurants.findByCode("demo").orElse(null);
        if (demo == null || ingredients.countByRestaurant(demo) > 0) {
            return;
        }
        Ingredient buns = add(demo, "Pain burger", "pièce", "80", "30");
        Ingredient beef = add(demo, "Steak haché", "kg", "9", "4");
        Ingredient cheddar = add(demo, "Cheddar", "kg", "2.5", "1");
        Ingredient potatoes = add(demo, "Pommes de terre", "kg", "30", "12");
        Ingredient salad = add(demo, "Salade", "kg", "4", "2");
        Ingredient goat = add(demo, "Chèvre", "kg", "1.8", "0.8");
        Ingredient dough = add(demo, "Pâte à pizza", "pièce", "40", "15");
        Ingredient mozzarella = add(demo, "Mozzarella", "kg", "5", "2");
        Ingredient tomato = add(demo, "Sauce tomate", "L", "6", "2");

        dish(demo, "Burger maison", new Object[][] {{buns, "1"}, {beef, "0.15"}, {cheddar, "0.03"}, {salad, "0.02"}});
        dish(demo, "Frites", new Object[][] {{potatoes, "0.25"}});
        dish(demo, "Salade de chèvre chaud", new Object[][] {{salad, "0.12"}, {goat, "0.08"}});
        dish(demo, "Pizza margherita", new Object[][] {{dough, "1"}, {mozzarella, "0.12"}, {tomato, "0.08"}});
    }

    private Ingredient add(Restaurant demo, String name, String unit, String quantity, String threshold) {
        return ingredients.save(new Ingredient(demo, name, unit, new BigDecimal(quantity), new BigDecimal(threshold)));
    }

    private void dish(Restaurant demo, String name, Object[][] lines) {
        Dish dish = new Dish(demo, name);
        for (Object[] line : lines) {
            dish.getLines().add(new RecipeLine(dish, (Ingredient) line[0], new BigDecimal((String) line[1])));
        }
        dishes.save(dish);
    }
}
