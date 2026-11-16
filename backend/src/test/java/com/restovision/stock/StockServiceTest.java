package com.restovision.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restovision.stock.StockDtos.DishRequest;
import com.restovision.stock.StockDtos.ImportResultDto;
import com.restovision.stock.StockDtos.IngredientDto;
import com.restovision.stock.StockDtos.IngredientRequest;
import com.restovision.stock.StockDtos.RecipeLineRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class StockServiceTest {
    @Autowired StockService stock;

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private IngredientDto ingredient(String name) {
        return stock.overview("demo").ingredients().stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theDemoRestaurantStartsWithAStockAndRecipes() {
        assertThat(stock.overview("demo").ingredients()).extracting(IngredientDto::name).contains("Pain burger", "Mozzarella");
        assertThat(stock.overview("demo").dishes()).extracting("name").contains("Burger maison", "Pizza margherita");
    }

    @Test
    void importedSalesTakeTheirRecipeOutOfTheStockOnce() {
        IngredientDto rice = stock.saveIngredient("demo", null, new IngredientRequest("Riz", "kg", n("10"), n("9")));
        IngredientDto curry = stock.saveIngredient("demo", null, new IngredientRequest("Pâte de curry", "kg", n("1"), n("0.2")));
        stock.saveDish("demo", null, new DishRequest("Curry de légumes", List.of(
                new RecipeLineRequest(rice.id(), n("0.2")), new RecipeLineRequest(curry.id(), n("0.03")))));

        byte[] file = "date;plat;quantite\n2026-11-14;CURRY DE LEGUMES;10\n2026-11-14;Plat inconnu;3\n2026-11-14;Plat inconnu;1\nxx;Riz;1\n"
                .getBytes(StandardCharsets.UTF_8);
        ImportResultDto result = stock.importSales("demo", "ventes.csv", file);

        assertThat(result.lines()).isEqualTo(3);
        assertThat(result.dishesSold()).isEqualByComparingTo("14");
        assertThat(result.unknownDishes()).singleElement()
                .satisfies(unknown -> assertThat(unknown.quantity()).isEqualByComparingTo("4"));
        assertThat(result.problems()).singleElement().satisfies(problem -> assertThat(problem.line()).isEqualTo(5));
        assertThat(ingredient("Riz").quantity()).isEqualByComparingTo("8");            // 10 - 10 x 0,2
        assertThat(ingredient("Riz").low()).isTrue();                                  // sous le seuil de 9
        assertThat(ingredient("Pâte de curry").quantity()).isEqualByComparingTo("0.7");
        assertThat(stock.overview("demo").lastImport().fileName()).isEqualTo("ventes.csv");

        assertThatThrownBy(() -> stock.importSales("demo", "ventes.csv", file))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("déjà été importé");
        assertThat(ingredient("Riz").quantity()).isEqualByComparingTo("8");
    }

    @Test
    void anIngredientUsedInARecipeCannotBeDeletedAndNamesStayUnique() {
        IngredientDto basil = stock.saveIngredient("demo", null, new IngredientRequest("Basilic", "kg", n("1"), n("0.1")));
        stock.saveDish("demo", null, new DishRequest("Pâtes au pesto", List.of(new RecipeLineRequest(basil.id(), n("0.02")))));

        assertThatThrownBy(() -> stock.deleteIngredient("demo", basil.id())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> stock.saveIngredient("demo", null, new IngredientRequest("basilic", "kg", n("1"), n("0"))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(stock.saveIngredient("demo", basil.id(), new IngredientRequest("Basilic", "kg", n("3"), n("0.1"))).quantity())
                .isEqualByComparingTo("3");
    }

    @Test
    void aFileWithNothingReadableChangesNothing() {
        assertThatThrownBy(() -> stock.importSales("demo", "vide.csv", "date;plat;quantite\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> stock.importSales("demo", "autre.csv", "a;b\n1;2\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
