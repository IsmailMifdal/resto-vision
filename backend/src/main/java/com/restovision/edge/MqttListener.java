package com.restovision.edge;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reçoit les événements des boîtiers. Se reconnecte seul toutes les 10 secondes si le broker tombe. */
@Component
@ConditionalOnProperty(name = "restovision.mqtt.enabled", havingValue = "true")
public class MqttListener implements MqttCallback {
    private static final Logger log = LoggerFactory.getLogger(MqttListener.class);

    private final String url;
    private final String topic;
    private final String layoutTopic;
    private final String username;
    private final String password;
    private final ObjectMapper mapper;
    private final EdgeEventService service;
    private final ReleaseTracker releases;
    private MqttClient client;
    /** Vrai une fois l'application démarrée, données de démonstration comprises. */
    private volatile boolean ready;

    public MqttListener(@Value("${restovision.mqtt.url}") String url,
                        @Value("${restovision.mqtt.topic}") String topic,
                        @Value("${restovision.mqtt.layout-topic:restovision/+/layout}") String layoutTopic,
                        @Value("${restovision.mqtt.username:}") String username,
                        @Value("${restovision.mqtt.password:}") String password,
                        ObjectMapper mapper, EdgeEventService service, ReleaseTracker releases) {
        this.url = url;
        this.topic = topic;
        this.layoutTopic = layoutTopic;
        this.username = username;
        this.password = password;
        this.mapper = mapper;
        this.service = service;
        this.releases = releases;
    }

    /** On n'écoute le broker qu'après le démarrage complet : un message ne doit pas croiser l'initialisation. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ready = true;
        ensureConnected();
    }

    @Scheduled(fixedDelay = 10_000)
    public synchronized void ensureConnected() {
        if (!ready) {
            return;
        }
        try {
            if (client == null) {
                client = new MqttClient(url, "restovision-backend-" + MqttClient.generateClientId(), new MemoryPersistence());
                client.setCallback(this);
            }
            if (!client.isConnected()) {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setConnectionTimeout(5);
                if (!username.isBlank()) {
                    options.setUserName(username);
                    options.setPassword(password.toCharArray());
                }
                client.connect(options);
                client.subscribe(new String[] {topic, layoutTopic, "restovision/updates"}, new int[] {1, 1, 1});
                log.info("Connecté au broker MQTT {} (topic {})", url, topic);
            }
        } catch (MqttException e) {
            log.warn("Broker MQTT injoignable ({}), nouvel essai dans 10 s : {}", url, e.getMessage());
        }
    }

    @Override
    public void messageArrived(String messageTopic, MqttMessage message) {
        if ("restovision/updates".equals(messageTopic)) {
            releases.onManifest(message.getPayload());
            return;
        }
        try {
            service.handle(mapper.readValue(message.getPayload(), EdgeEvent.class));
        } catch (Exception e) {
            // Un message invalide ne doit jamais couper la connexion.
            log.warn("Message MQTT ignoré sur {} : {}", messageTopic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Connexion MQTT perdue : {}", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // rien : le backend ne publie pas
    }

    @PreDestroy
    public synchronized void close() {
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            }
        } catch (MqttException e) {
            log.debug("Fermeture MQTT : {}", e.getMessage());
        }
    }
}
