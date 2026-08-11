package ma.quizgen.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Requête de soumission des réponses d'un étudiant.
 * La Map est { "question-uuid": "réponse de l'étudiant" }.
 */
public record SubmitAnswersRequest(
    @NotNull(message = "Les réponses sont obligatoires")
    Map<String, String> answers
) {}
