package ma.quizgen.dto;

import java.util.List;
import java.util.UUID;

public record QuizAnalyticsDto(
    UUID quizId,
    String quizTitle,
    long totalAttempts,
    double averageScore,
    double successRate,
    List<QuestionStatDto> questionStats
) {
    public record QuestionStatDto(
        UUID questionId,
        String content,
        long totalAnswers,
        long correctAnswers,
        double correctRate
    ) {}
}
