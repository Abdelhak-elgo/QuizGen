package ma.quizgen.entity.enums;

/** Verdict d'une réponse d'étudiant après correction. */
public enum AnswerOutcome {
    CORRECT,
    PARTIAL,     // réponse ouverte partiellement juste (BERTScore)
    INCORRECT,
    UNANSWERED,
    UNGRADED     // réponse ouverte non corrigée automatiquement (BERTScore indisponible)
}
