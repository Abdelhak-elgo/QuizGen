package ma.quizgen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ma.quizgen.entity.enums.AnswerOutcome;
import ma.quizgen.entity.enums.QuestionType;

import java.util.List;

/**
 * Copie corrigée envoyée au microservice NLP — POST /recommendations.
 * Volontairement anonyme : aucune donnée nominative de l'étudiant.
 */
public record NlpRecommendationRequest(
    @JsonProperty("quiz_title") String quizTitle,
    double score,
    @JsonProperty("max_score") int maxScore,
    List<Item> items
) {
    public record Item(
        String question,
        QuestionType type,
        @JsonProperty("correct_answer") String correctAnswer,
        @JsonProperty("student_answer") String studentAnswer,
        AnswerOutcome outcome,
        String explanation
    ) {}
}
