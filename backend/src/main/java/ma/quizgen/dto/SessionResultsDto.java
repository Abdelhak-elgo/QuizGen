package ma.quizgen.dto;

import ma.quizgen.entity.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Résultats d'une session : un étudiant par ligne (assigné ou ayant rejoint) + synthèse. */
public record SessionResultsDto(
    UUID sessionId,
    UUID quizId,
    String quizTitle,
    int nbQuestions,
    SessionStatus sessionStatus,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Summary summary,
    List<StudentResult> students
) {
    public enum StudentStatus { NOT_STARTED, IN_PROGRESS, SUBMITTED, EXPIRED }

    public record Summary(
        int totalStudents,
        int notStarted,
        int inProgress,
        int submitted,
        /** Moyenne des copies soumises, en % ; null sans copie soumise. */
        Double averagePercent,
        /** Part des copies soumises ≥ seuil de réussite, en % ; null sans copie soumise. */
        Double passRate
    ) {}

    public record StudentResult(
        UUID studentId,
        String firstName,
        String lastName,
        String email,
        boolean assigned,
        StudentStatus status,
        UUID attemptId,
        Integer score,
        Integer maxScore,
        Double percent,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        boolean hasRecommendation
    ) {}
}
