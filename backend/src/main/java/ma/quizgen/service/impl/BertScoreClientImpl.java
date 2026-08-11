package ma.quizgen.service.impl;

import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.BertScoreDetailDto;
import ma.quizgen.entity.Question;
import ma.quizgen.service.BertScoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Implémentation du client BERTScore via le microservice FastAPI NLP.
 *
 * Utilise le endpoint {@code POST /score/batch} pour traiter toutes les réponses
 * ouvertes d'une tentative en une seule requête HTTP.
 *
 * En cas d'indisponibilité du service NLP, retourne des scores à 0 (INCORRECT)
 * pour ne pas bloquer la soumission de l'étudiant.
 */
@Service
@Slf4j
public class BertScoreClientImpl implements BertScoreClient {

    private final RestTemplate restTemplate;
    private final String nlpServiceUrl;

    public BertScoreClientImpl(
        RestTemplate restTemplate,
        @Value("${nlp.service.url:http://nlp-service:8000}") String nlpServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.nlpServiceUrl = nlpServiceUrl;
    }

    @Override
    public List<BertScoreDetailDto> scoreOpenAnswers(
        List<Question> openQuestions,
        Map<String, String> studentAnswers
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

            return mapResults(results, answeredQuestions, studentAnswers);

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
        Map<String, String> studentAnswers
    ) {
        Map<String, Question> qMap = new HashMap<>();
        for (Question q : questions) qMap.put(q.getId().toString(), q);

        List<BertScoreDetailDto> dtos = new ArrayList<>();
        for (Map<String, Object> r : results) {
            String qId = (String) r.getOrDefault("question_id", "");
            Question q = qMap.get(qId);
            if (q == null) continue;

            dtos.add(new BertScoreDetailDto(
                qId,
                q.getContent(),
                studentAnswers.getOrDefault(qId, ""),
                q.getCorrectAnswer(),
                toDouble(r.get("f1")),
                toDouble(r.get("precision")),
                toDouble(r.get("recall")),
                toDouble(r.get("partial_score")),
                (String) r.getOrDefault("label", "INCORRECT"),
                (String) r.getOrDefault("model", "unavailable")
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
                "unavailable"
            ));
        }
        return result;
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}
