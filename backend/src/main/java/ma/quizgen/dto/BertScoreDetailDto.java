package ma.quizgen.dto;

/**
 * Détail BERTScore pour une réponse ouverte/exercice.
 * Inclus dans {@link AttemptDto#bertScoreDetails()} indexé par question_id.
 */
public record BertScoreDetailDto(
    String questionId,
    String questionContent,
    String studentAnswer,
    String referenceAnswer,
    double f1,
    double precision,
    double recall,
    double partialScore,
    String label,       // CORRECT | PARTIEL | INCORRECT
    String model
) {}
