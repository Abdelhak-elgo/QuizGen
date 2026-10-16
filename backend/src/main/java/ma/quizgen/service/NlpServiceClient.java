package ma.quizgen.service;

import ma.quizgen.dto.NlpGenerateRequest;
import ma.quizgen.dto.NlpRecommendationRequest;
import ma.quizgen.dto.NlpTaskStatus;

import java.util.Map;

public interface NlpServiceClient {

    /**
     * Soumet une demande de génération au microservice FastAPI.
     * @return task_id Celery retourné par FastAPI
     */
    String submitGeneration(NlpGenerateRequest request);

    /**
     * Interroge l'état d'une tâche de génération.
     * @param taskId ID de la tâche Celery
     */
    NlpTaskStatus pollStatus(String taskId);

    /**
     * Annule une génération (quiz supprimé) pour ne pas consommer d'appels LLM
     * inutiles. Au mieux : un échec est journalisé, jamais propagé.
     */
    void cancelGeneration(String taskId);

    /**
     * Diagnostic pédagogique d'une copie corrigée (appel LLM synchrone).
     * @return réponse brute du microservice (clés snake_case)
     * @throws IllegalStateException si le microservice ou le LLM échoue (message exploitable)
     */
    Map<String, Object> requestRecommendation(NlpRecommendationRequest request);

    /**
     * Soumet et attend (polling) la fin de la génération.
     * Lève une exception si timeout ou échec NLP.
     * @return NlpTaskStatus final (SUCCESS)
     */
    NlpTaskStatus submitAndWait(NlpGenerateRequest request);
}
