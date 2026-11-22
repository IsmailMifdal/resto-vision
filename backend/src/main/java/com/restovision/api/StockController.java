package com.restovision.api;

import com.restovision.stock.StockDtos.DishDto;
import com.restovision.stock.StockDtos.DishRequest;
import com.restovision.stock.StockDtos.ImportResultDto;
import com.restovision.stock.StockDtos.IngredientDto;
import com.restovision.stock.StockDtos.IngredientRequest;
import com.restovision.stock.StockDtos.StockDto;
import com.restovision.stock.OrderService;
import com.restovision.stock.StockDtos.OrderDto;
import com.restovision.stock.StockService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Stock : lecture pour toute l'équipe, modifications réservées au gérant (voir SecurityConfig). */
@RestController
@RequestMapping("/api/stock")
public class StockController {
    private static final long MAX_FILE_BYTES = 2_000_000;
    private final StockService stock;
    private final OrderService orders;

    public StockController(StockService stock, OrderService orders) {
        this.stock = stock;
        this.orders = orders;
    }

    private static String restaurantOf(Jwt jwt) {
        return jwt.getClaimAsString("restaurant");
    }

    @GetMapping
    public StockDto overview(@AuthenticationPrincipal Jwt jwt) {
        return stock.overview(restaurantOf(jwt));
    }

    @PostMapping("/ingredients")
    public IngredientDto addIngredient(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IngredientRequest request) {
        return stock.saveIngredient(restaurantOf(jwt), null, request);
    }

    @PutMapping("/ingredients/{id}")
    public IngredientDto updateIngredient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody IngredientRequest request) {
        return stock.saveIngredient(restaurantOf(jwt), id, request);
    }

    @DeleteMapping("/ingredients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIngredient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        stock.deleteIngredient(restaurantOf(jwt), id);
    }

    @PostMapping("/dishes")
    public DishDto addDish(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DishRequest request) {
        return stock.saveDish(restaurantOf(jwt), null, request);
    }

    @PutMapping("/dishes/{id}")
    public DishDto updateDish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody DishRequest request) {
        return stock.saveDish(restaurantOf(jwt), id, request);
    }

    @DeleteMapping("/dishes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        stock.deleteDish(restaurantOf(jwt), id);
    }

    /** Proposition de commande fournisseur pour tenir `days` jours. */
    @GetMapping("/order")
    public OrderDto order(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "7") int days) {
        return orders.proposal(restaurantOf(jwt), days);
    }

    /** Import d'un export de ventes de la caisse (CSV : date, plat, quantite). */
    @PostMapping(path = "/sales", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importSales(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier est vide ou dépasse 2 Mo.");
        }
        return stock.importSales(restaurantOf(jwt), file.getOriginalFilename(), file.getBytes());
    }
}
