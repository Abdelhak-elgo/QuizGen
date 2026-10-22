package ma.quizgen.dto;

import ma.quizgen.entity.Session;
import ma.quizgen.entity.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record SessionDto(
    UUID id,
    UUID quizId,
    String quizTitle,
    UUID teacherId,
    String teacherName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String accessCode,
    SessionStatus sessionStatus,
    int attemptCount,
    LocalDateTime createdAt,
    List<SessionParticipantDto> participants
) {
    public static SessionDto from(Session session) {
        List<SessionParticipantDto> participants = session.getParticipants() == null ? List.of()
            : session.getParticipants().stream()
                .map(SessionParticipantDto::from)
                .sorted(Comparator.comparing(SessionParticipantDto::lastName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new SessionDto(
            session.getId(),
            session.getQuiz().getId(),
            session.getQuiz().getTitle(),
            session.getTeacher().getId(),
            session.getTeacher().getFirstName() + " " + session.getTeacher().getLastName(),
            session.getStartTime(),
            session.getEndTime(),
            session.getAccessCode(),
            session.getSessionStatus(),
            session.getAttempts() == null ? 0 : session.getAttempts().size(),
            session.getCreatedAt(),
            participants
        );
    }
}
