package ma.quizgen.entity.enums;

public enum AttemptStatus {
    IN_PROGRESS, // L'étudiant a rejoint mais n'a pas soumis
    SUBMITTED,   // Soumis et corrigé
    EXPIRED      // Temps écoulé sans soumission
}
