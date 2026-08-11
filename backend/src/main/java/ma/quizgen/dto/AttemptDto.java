package ma.quizgen.dto;

import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.enums.AttemptStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AttemptDto(
    UUID id,
    UUID sessionId,
    UUID studentId,
    String studentName,
    Map<String, String> answers,
    Integer score,
    Integer maxScore,
    Double scorePercent,
    AttemptStatus attemptStatus,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {
    public static AttemptDto from(Attempt attempt) {
        double percent = (attempt.getMaxScore() != null && attempt.getMaxScore() > 0)
            ? attempt.getScore() * 100.0 / attempt.getMaxScore()
            : 0.0;
        return new AttemptDto(
            attempt.getId(),
            attempt.getSession().getId(),
            attempt.getStudent().getId(),
            attempt.getStudent().getFirstName() + " " + attempt.getStudent().getLastName(),
            attempt.getAnswers(),
            attempt.getScore(),
            attempt.getMaxScore(),
            Math.round(percent * 10.0) / 10.0,
            attempt.getAttemptStatus(),
            attempt.getStartedAt(),
            attempt.getCompletedAt()
        );
    }
}
