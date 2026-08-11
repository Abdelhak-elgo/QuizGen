package ma.quizgen.service;

import ma.quizgen.dto.NlpGenerateRequest;
import ma.quizgen.dto.NlpTaskStatus;

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
     * Soumet et attend (polling) la fin de la génération.
     * Lève une exception si timeout ou échec NLP.
     * @return NlpTaskStatus final (SUCCESS)
     */
    NlpTaskStatus submitAndWait(NlpGenerateRequest request);
}
