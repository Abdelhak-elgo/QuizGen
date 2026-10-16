package ma.quizgen.service.impl;

import ma.quizgen.entity.Question;
import ma.quizgen.entity.enums.AnswerOutcome;
import ma.quizgen.entity.enums.QuestionType;

import java.util.Map;

/**
 * Verdict d'une réponse, à partir des données enregistrées à la soumission :
 * comparaison exacte pour un QCM (comme le score), étiquette BERTScore pour une
 * réponse ouverte ou un exercice.
 */
final class AnswerGrader {

    /** Verdict et score partiel (réponses ouvertes uniquement, sinon null). */
    record Grade(AnswerOutcome outcome, Double partialScore) {}

    private AnswerGrader() {}

    static Grade grade(Question question, String answer, Map<String, Object> bertScoreDetails) {
        if (answer == null || answer.isBlank()) {
            return new Grade(AnswerOutcome.UNANSWERED, null);
        }
        if (question.getType() == QuestionType.QCM) {
            boolean correct = answer.trim().equalsIgnoreCase(question.getCorrectAnswer().trim());
            return new Grade(correct ? AnswerOutcome.CORRECT : AnswerOutcome.INCORRECT, null);
        }

        Object raw = bertScoreDetails == null ? null : bertScoreDetails.get(question.getId().toString());
        if (!(raw instanceof Map<?, ?> detail)) {
            return new Grade(AnswerOutcome.UNGRADED, null);
        }
        Double partial = detail.get("partialScore") instanceof Number n ? n.doubleValue() : null;
        String label = String.valueOf(detail.get("label")).toUpperCase();
        AnswerOutcome outcome = switch (label) {
            case "CORRECT" -> AnswerOutcome.CORRECT;
            case "PARTIEL", "PARTIAL" -> AnswerOutcome.PARTIAL;
            case "INCORRECT" -> AnswerOutcome.INCORRECT;
            default -> AnswerOutcome.UNGRADED;
        };
        return new Grade(outcome, partial);
    }
}
