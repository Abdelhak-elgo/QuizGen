package ma.quizgen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.BertScoreDetailDto;
import ma.quizgen.entity.Question;
import ma.quizgen.service.BertScoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Implémentation du client BERTScore + Evidence RAG via le microservice FastAPI NLP.
 *
 * <h3>Pipeline de correction</h3>
 * <ol>
 *   <li>Appel batch POST /score/batch → BERTScore F1 pour toutes les réponses ouvertes</li>
 *   <li>Pour chaque question OUVERTE corrigée, appel optionnel POST /evidence (timeout 3 s)
 *       → passage source ChromaDB le plus proche de la réponse étudiant</li>
 * </ol>
 *
 * En cas d'indisponibilité du service NLP ou de ChromaDB, retourne des scores à 0
 * (INCORRECT) et {@code evidenceChunk: null} pour ne pas bloquer la soumission.
 */
@Service
@Slf4j
public class BertScoreClientImpl implements BertScoreClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String nlpServiceUrl;

    /** Client HTTP Java 11 avec timeout de connexion 3 s — utilisé pour /evidence. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    public BertScoreClientImpl(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${nlp.service.url:http://nlp-service:8000}") String nlpServiceUrl
    ) {
        this.restTemplate  = restTemplate;
        this.objectMapper  = objectMapper;
        this.nlpServiceUrl = nlpServiceUrl;
    }

    @Override
    public List<BertScoreDetailDto> scoreOpenAnswers(
        List<Question> openQuestions,
        Map<String, String> studentAnswers,
        String documentId
    ) {
        if (openQuestions == null || openQuestions.isEmpty()) return List.of();

        // Construire les items uniquement pour les questions ayant une réponse
        List<Map<String, Object>> items = new ArrayList<>();
        List<Question> answeredQuestions = new ArrayList<>();

        for (Question q : openQuestions) {
            String qId = q.getId().toString();
            String studentAnswer = studentAnswers.get(qId);
            if (studentAnswer == null || studentAnswer.isBlank()) continue;

            items.add(Map.of(
                "candidate", studentAnswer.trim(),
                "reference", q.getCorrectAnswer().trim(),
                "question_id", qId
            ));
            answeredQuestions.add(q);
        }

        if (items.isEmpty()) return List.of();

        try {
            Map<String, Object> requestBody = Map.of("items", items);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                nlpServiceUrl + "/score/batch",
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("BERTScore batch returned non-OK status: {}", response.getStatusCode());
                return buildFallbackResults(answeredQuestions, studentAnswers);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
            if (results == null) return buildFallbackResults(answeredQuestions, studentAnswers);

            return mapResults(results, answeredQuestions, studentAnswers, documentId);

        } catch (RestClientException e) {
            log.warn("NLP service indisponible pour BERTScore ({}): {}", nlpServiceUrl, e.getMessage());
            return buildFallbackResults(answeredQuestions, studentAnswers);
        } catch (Exception e) {
            log.error("Erreur inattendue BERTScore: {}", e.getMessage(), e);
            return buildFallbackResults(answeredQuestions, studentAnswers);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<BertScoreDetailDto> mapResults(
        List<Map<String, Object>> results,
        List<Question> questions,
        Map<String, String> studentAnswers,
        String documentId
    ) {
        Map<String, Question> qMap = new HashMap<>();
        for (Question q : questions) qMap.put(q.getId().toString(), q);

        List<BertScoreDetailDto> dtos = new ArrayList<>();
        for (Map<String, Object> r : results) {
            String qId = (String) r.getOrDefault("question_id", "");
            Question q = qMap.get(qId);
            if (q == null) continue;

            String studentAnswer = studentAnswers.getOrDefault(qId, "");

            // Récupérer l'evidence RAG pour cette réponse (timeout 3 s, non-bloquant)
            Map<String, Object> evidenceChunk = fetchEvidence(documentId, studentAnswer);

            dtos.add(new BertScoreDetailDto(
                qId,
                q.getContent(),
                studentAnswer,
                q.getCorrectAnswer(),
                toDouble(r.get("f1")),
                toDouble(r.get("precision")),
                toDouble(r.get("recall")),
                toDouble(r.get("partial_score")),
                (String) r.getOrDefault("label", "INCORRECT"),
                (String) r.getOrDefault("model", "unavailable"),
                evidenceChunk
            ));
        }
        return dtos;
    }

    private List<BertScoreDetailDto> buildFallbackResults(
        List<Question> questions,
        Map<String, String> studentAnswers
    ) {
        List<BertScoreDetailDto> result = new ArrayList<>();
        for (Question q : questions) {
            String qId = q.getId().toString();
            result.add(new BertScoreDetailDto(
                qId,
                q.getContent(),
                studentAnswers.getOrDefault(qId, ""),
                q.getCorrectAnswer(),
                0.0, 0.0, 0.0, 0.0,
                "INCORRECT",
                "unavailable",
                null    // pas d'evidence en cas de fallback
            ));
        }
        return result;
    }

    /**
     * Appelle POST /evidence du NLP service pour récupérer le passage source
     * ChromaDB le plus proche de la réponse étudiant.
     *
     * <p>Non-bloquant : timeout de lecture 3 s. En cas d'échec (timeout,
     * service indisponible, document non indexé), retourne {@code null}
     * silencieusement — la correction BERTScore n'est pas affectée.</p>
     *
     * @param documentId    UUID du document source (peut être null)
     * @param studentAnswer Réponse de l'étudiant utilisée comme requête de similarité
     * @return Map {"text": "...", "similarity_score": 0.72} ou null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchEvidence(String documentId, String studentAnswer) {
        if (documentId == null || documentId.isBlank()) return null;
        if (studentAnswer == null || studentAnswer.isBlank()) return null;

        try {
            String bodyJson = objectMapper.writeValueAsString(Map.of(
                "document_id",   documentId,
                "student_answer", studentAnswer.trim()
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(nlpServiceUrl + "/evidence"))
                .timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type", "application/json")
                .build();

            HttpResponse<String> httpResponse = HTTP_CLIENT.send(
                httpRequest, HttpResponse.BodyHandlers.ofString()
            );

            if (httpResponse.statusCode() != 200) {
                log.debug("Evidence RAG non-OK status {} pour doc_id={}", httpResponse.statusCode(), documentId);
                return null;
            }

            Map<String, Object> parsed = objectMapper.readValue(httpResponse.body(), Map.class);

            // Retourner null si evidence_text est absent ou null (document non indexé)
            Object evidenceText = parsed.get("evidence_text");
            if (evidenceText == null) return null;

            // Renommer les clés pour cohérence frontend
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("text",             evidenceText.toString());
            chunk.put("similarity_score", parsed.getOrDefault("similarity_score", 0.0));
            return chunk;

        } catch (java.net.http.HttpTimeoutException e) {
            log.debug("Evidence RAG timeout (3s) pour doc_id={}", documentId);
            return null;
        } catch (Exception e) {
            log.debug("Evidence RAG erreur pour doc_id={} : {}", documentId, e.getMessage());
            return null;
        }
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}
