package ma.quizgen.entity.enums;

public enum SessionStatus {
    SCHEDULED,  // Planifiée, pas encore ouverte
    OPEN,       // En cours (entre start_time et end_time)
    CLOSED,     // Terminée
    CANCELLED   // Annulée par l'enseignant
}
