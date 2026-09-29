package ma.quizgen.dto;

import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.Session;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Session à laquelle un étudiant est assigné, avec l'état de sa tentative
 * (null tant qu'il ne l'a pas commencée) — vue tableau de bord étudiant.
 */
public record AssignedSessionDto(
    UUID id,
    UUID quizId,
    String quizTitle,
    int nbQuestions,
    String teacherName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    SessionStatus sessionStatus,
    UUID attemptId,
    AttemptStatus attemptStatus
) {
    public static AssignedSessionDto from(Session session, Attempt attempt) {
        return new AssignedSessionDto(
            session.getId(),
            session.getQuiz().getId(),
            session.getQuiz().getTitle(),
            session.getQuiz().getNbQuestions(),
            session.getTeacher().getFirstName() + " " + session.getTeacher().getLastName(),
            session.getStartTime(),
            session.getEndTime(),
            session.getSessionStatus(),
            attempt == null ? null : attempt.getId(),
            attempt == null ? null : attempt.getAttemptStatus()
        );
    }
}
