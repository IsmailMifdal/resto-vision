package com.restovision.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Routes réservées pour la V2 et la V3. Elles répondent 501 tant que le module n'est pas codé. */
@RestController
@RequestMapping("/api")
public class ComingSoonController {

    private static ResponseEntity<Map<String, String>> planned(String feature, String version) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("feature", feature, "plannedFor", version));
    }





    @GetMapping("/sites")
    public ResponseEntity<Map<String, String>> sites() {
        return planned("Comparaison multi-sites", "V3");
    }
}
