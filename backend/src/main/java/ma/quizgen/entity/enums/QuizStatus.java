package ma.quizgen.entity.enums;

public enum QuizStatus {
    DRAFT,       // En cours de génération NLP
    REVIEWING,   // Généré, en attente de validation par l'enseignant
    PUBLISHED,   // Publié et disponible pour les sessions
    ARCHIVED     // Archivé
}
