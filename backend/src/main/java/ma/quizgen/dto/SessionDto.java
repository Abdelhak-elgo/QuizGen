package ma.quizgen.dto;

import ma.quizgen.entity.Session;
import ma.quizgen.entity.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionDto(
    UUID id,
    UUID quizId,
    String quizTitle,
    UUID teacherId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String accessCode,
    SessionStatus sessionStatus,
    int attemptCount,
    LocalDateTime createdAt
) {
    public static SessionDto from(Session session) {
        return new SessionDto(
            session.getId(),
            session.getQuiz().getId(),
            session.getQuiz().getTitle(),
            session.getTeacher().getId(),
            session.getStartTime(),
            session.getEndTime(),
            session.getAccessCode(),
            session.getSessionStatus(),
            session.getAttempts() == null ? 0 : session.getAttempts().size(),
            session.getCreatedAt()
        );
    }
}
