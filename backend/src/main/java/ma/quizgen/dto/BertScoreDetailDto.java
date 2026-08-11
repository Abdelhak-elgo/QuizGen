package ma.quizgen.dto;

import java.util.Map;

/**
 * Détail BERTScore pour une réponse ouverte/exercice.
 * Inclus dans {@link AttemptDto#bertScoreDetails()} indexé par question_id.
 *
 * <p>Le champ {@code evidenceChunk} est nullable : il est renseigné quand
 * ChromaDB est disponible et que le document a été indexé lors de la génération.
 * Il contient le passage du document source le plus proche de la réponse de
 * l'étudiant, permettant une correction explicable (XAI).</p>
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
    String label,           // CORRECT | PARTIEL | INCORRECT
    String model,
    Map<String, Object> evidenceChunk   // {"text": "...", "similarity_score": 0.72} | null
) {}
