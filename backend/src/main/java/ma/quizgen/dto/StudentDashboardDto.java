package ma.quizgen.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StudentDashboardDto(
    UUID studentId,
    String studentName,
    int totalAttempts,
    double averageScore,
    List<AttemptSummaryDto> recentAttempts
) {
    public record AttemptSummaryDto(
        UUID attemptId,
        UUID sessionId,
        String quizTitle,
        Integer score,
        Integer maxScore,
        Double scorePercent,
        String status,
        LocalDateTime completedAt
    ) {}
}
