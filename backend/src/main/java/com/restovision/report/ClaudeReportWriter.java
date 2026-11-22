package com.restovision.report;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rédaction par Claude. Le modèle reçoit les chiffres déjà calculés, en JSON, et rien d'autre.
 * Actif seulement si une clé d'API est fournie (ANTHROPIC_API_KEY).
 */
@Component
public class ClaudeReportWriter implements ReportWriter {
    static final String INSTRUCTIONS = """
            Tu rédiges le rapport de fin de service d'un restaurant pour son gérant. Il le lit en une minute, \
            souvent sur son téléphone, pour savoir comment la journée s'est passée et quoi regarder demain.

            Le message de l'utilisateur contient les chiffres de la journée en JSON, mesurés par les caméras de la \
            salle. Les durées sont en secondes. "hours" donne chaque heure de service : 12 veut dire de 12 h à 13 h. \
            "averageWaitSeconds" est l'attente entre l'installation des clients et leur service. \
            "weekAverageWaitSeconds" est la même mesure sur les 7 derniers jours. Un champ à null n'a pas pu être mesuré.

            Écris en français un seul paragraphe de 4 à 6 phrases, en texte simple, sans titre, sans liste et sans \
            mise en forme. Commence par ce qui a le plus pesé sur le service. Donne les durées en minutes et secondes \
            lisibles. Termine par une piste concrète pour le prochain service, seulement si les chiffres la justifient.

            Tous les chiffres que tu cites viennent du JSON : ce rapport sert à prendre des décisions, et un chiffre \
            inventé ou arrondi à l'excès tromperait le gérant. Tu ne connais ni l'équipe, ni la carte, ni la météo : \
            n'en parle pas. Si "dayInProgress" est vrai, dis que la journée n'est pas terminée.
            """;

    private final ObjectMapper mapper;
    private final String model;
    private final AnthropicClient client;

    public ClaudeReportWriter(ObjectMapper mapper,
                              @Value("${restovision.report.anthropic-api-key:}") String apiKey,
                              @Value("${restovision.report.model:claude-opus-5-5}") String model) {
        this.mapper = mapper;
        this.model = model;
        this.client = apiKey.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public String write(ReportFacts facts) {
        if (client == null) {
            throw new IllegalStateException("Aucune clé d'API pour le rapport par IA");
        }
        String json;
        try {
            json = mapper.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Message response = client.messages().create(MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(INSTRUCTIONS)
                // Mettre des chiffres en phrases ne demande pas un long raisonnement.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(json)
                .build());
        // Une réponse refusée ou coupée n'est pas un rapport : l'appelant repassera aux phrases toutes faites.
        String stop = response.stopReason().map(Object::toString).orElse("");
        if (!stop.equals("end_turn")) {
            throw new IllegalStateException("Réponse incomplète du modèle : " + stop);
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining()).strip();
        if (text.isEmpty()) {
            throw new IllegalStateException("Réponse vide du modèle");
        }
        return text;
    }
}
