package com.restovision.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dernière version du boîtier publiée sur `restovision/updates`. Le broker conserve ce message : il revient à
 * chaque connexion, rien n'a besoin d'être gardé en base. Le backend ne vérifie ni n'installe rien : il affiche
 * seulement la version attendue à côté de celle de chaque boîtier.
 */
@Component
public class ReleaseTracker {
    private static final Logger log = LoggerFactory.getLogger(ReleaseTracker.class);
    private final ObjectMapper mapper;
    private volatile String latest;

    public ReleaseTracker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String latest() {
        return latest;
    }

    public void onManifest(byte[] payload) {
        if (payload == null || payload.length == 0) {
            latest = null;   // version retirée
            return;
        }
        try {
            JsonNode version = mapper.readTree(payload).path("version");
            if (version.isTextual() && version.asText().matches("\\d+(\\.\\d+){0,3}")) {
                latest = version.asText();
                log.info("Version publiée du boîtier : {}", latest);
            }
        } catch (Exception e) {
            log.warn("Manifeste de mise à jour illisible : {}", e.getMessage());
        }
    }
}
