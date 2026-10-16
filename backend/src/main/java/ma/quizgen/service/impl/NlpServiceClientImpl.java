package ma.quizgen.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.NlpGenerateRequest;
import ma.quizgen.dto.NlpRecommendationRequest;
import ma.quizgen.dto.NlpTaskStatus;
import ma.quizgen.service.NlpServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NlpServiceClientImpl implements NlpServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${nlp.service.url:http://nlp-service:8000}")
    private String nlpBaseUrl;

    @Value("${nlp.service.poll-interval-ms:3000}")
    private long pollIntervalMs;

    @Value("${nlp.service.timeout-ms:300000}")
    private long timeoutMs;

    @Override
    public String submitGeneration(NlpGenerateRequest request) {
        String url = nlpBaseUrl + "/generate";
        log.info("Soumission génération NLP → {} (document={})", url, request.documentId());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response == null || !response.containsKey("task_id")) {
                throw new IllegalStateException("Le microservice NLP n'a pas retourné de task_id");
            }
            String taskId = (String) response.get("task_id");
            log.info("Tâche NLP soumise — task_id={}", taskId);
            return taskId;
        } catch (RestClientException e) {
            throw new IllegalStateException("Impossible de joindre le microservice NLP : " + e.getMessage(), e);
        }
    }

    @Override
    public NlpTaskStatus pollStatus(String taskId) {
        String url = nlpBaseUrl + "/status/" + taskId;
        try {
            NlpTaskStatus status = restTemplate.getForObject(url, NlpTaskStatus.class);
            if (status == null) {
                throw new IllegalStateException("Réponse vide pour task_id=" + taskId);
            }
            return status;
        } catch (RestClientException e) {
            throw new IllegalStateException("Erreur polling NLP task_id=" + taskId + " : " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelGeneration(String taskId) {
        try {
            restTemplate.delete(nlpBaseUrl + "/tasks/" + taskId);
            log.info("Tâche NLP annulée — task_id={}", taskId);
        } catch (RestClientException e) {
            log.warn("Annulation de la tâche NLP {} impossible : {}", taskId, e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestRecommendation(NlpRecommendationRequest request) {
        String url = nlpBaseUrl + "/recommendations";
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response == null) {
                throw new IllegalStateException("Réponse vide du microservice NLP");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            // Le microservice renvoie { "detail": "…" } : le transmettre à l'enseignant
            throw new IllegalStateException(nlpDetail(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Microservice NLP injoignable : " + e.getMessage(), e);
        }
    }

    private String nlpDetail(HttpStatusCodeException e) {
        try {
            JsonNode detail = objectMapper.readTree(e.getResponseBodyAsString()).path("detail");
            if (detail.isTextual() && !detail.asText().isBlank()) return detail.asText();
        } catch (JsonProcessingException ignored) {
            // corps non JSON : message générique ci-dessous
        }
        return "Erreur du microservice NLP (HTTP " + e.getStatusCode().value() + ")";
    }

    @Override
    public NlpTaskStatus submitAndWait(NlpGenerateRequest request) {
        String taskId = submitGeneration(request);
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            NlpTaskStatus status = pollStatus(taskId);
            log.debug("NLP status={}, progress={}%", status.status(), status.progress());

            if (status.isSuccess()) {
                log.info("✅ Génération NLP terminée — task_id={}", taskId);
                return status;
            }
            if (status.isFailure()) {
                throw new IllegalStateException(
                    "La génération NLP a échoué (task_id=" + taskId + ") : " + status.error()
                );
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Polling NLP interrompu", e);
            }
        }

        throw new IllegalStateException(
            "Timeout de la génération NLP après " + (timeoutMs / 1000) + "s (task_id=" + taskId + ")"
        );
    }
}
