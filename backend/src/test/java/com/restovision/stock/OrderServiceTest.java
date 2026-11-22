package com.restovision.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.restovision.domain.Restaurant;
import com.restovision.stock.StockDtos.OrderLineDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private final Restaurant restaurant = new Restaurant("demo", "Démo");

    private Ingredient ingredient(String unit, String quantity, String threshold) {
        return new Ingredient(restaurant, "Steak haché", unit, new BigDecimal(quantity), new BigDecimal(threshold));
    }

    @Test
    void ordersEnoughToLastTheCoverAndStayAboveTheThreshold() {
        // 10,2 kg vendus sur 2 jours = 5,1 kg par jour. 7 jours = 35,7 kg, plus 4 kg de seuil, moins 3,9 kg en stock.
        OrderLineDto line = OrderService.line(ingredient("kg", "3.9", "4"), new BigDecimal("10.2"), 2, 7);

        assertThat(line.dailyUse()).isEqualByComparingTo("5.1");
        assertThat(line.daysLeft()).isEqualByComparingTo("0.7");
        assertThat(line.toOrder()).isEqualByComparingTo("35.8");
    }

    @Test
    void wellStockedIngredientsAreNotOrdered() {
        OrderLineDto line = OrderService.line(ingredient("kg", "50", "4"), new BigDecimal("10.2"), 2, 7);

        assertThat(line.toOrder()).isEqualByComparingTo("0");
        assertThat(line.daysLeft()).isEqualByComparingTo("9.8");
    }

    @Test
    void withoutSalesOnlyWhatIsUnderItsThresholdIsOrdered() {
        OrderLineDto low = OrderService.line(ingredient("kg", "1", "4"), null, 0, 7);
        assertThat(low.dailyUse()).isNull();
        assertThat(low.daysLeft()).isNull();
        assertThat(low.toOrder()).isEqualByComparingTo("7");   // pour revenir à deux fois le seuil

        assertThat(OrderService.line(ingredient("kg", "6", "4"), null, 0, 7).toOrder()).isEqualByComparingTo("0");
    }

    @Test
    void countedUnitsAreOrderedWhole() {
        assertThat(OrderService.round(new BigDecimal("12.01"), "pièce")).isEqualByComparingTo("13");
        assertThat(OrderService.round(new BigDecimal("12.01"), "kg")).isEqualByComparingTo("12.1");
    }

    @Test
    void aNegativeStockIsOrderedBackUpAndDoesNotGiveNegativeDays() {
        OrderLineDto line = OrderService.line(ingredient("kg", "-2", "1"), new BigDecimal("4"), 2, 3);

        assertThat(line.daysLeft()).isEqualByComparingTo("0");
        assertThat(line.toOrder()).isEqualByComparingTo("9");   // 2 x 3 + 1 + 2
    }
}
