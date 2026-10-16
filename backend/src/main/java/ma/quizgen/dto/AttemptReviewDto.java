package ma.quizgen.dto;

import ma.quizgen.entity.enums.AnswerOutcome;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Copie d'un étudiant corrigée question par question — vue enseignant. */
public record AttemptReviewDto(
    UUID attemptId,
    UUID sessionId,
    UUID quizId,
    String quizTitle,
    UUID studentId,
    String studentName,
    String studentEmail,
    AttemptStatus attemptStatus,
    Integer score,
    Integer maxScore,
    Double percent,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    List<QuestionReview> questions,
    RecommendationDto recommendation
) {
    public record QuestionReview(
        UUID questionId,
        int position,
        QuestionType type,
        String content,
        List<String> options,
        String correctAnswer,
        String studentAnswer,
        AnswerOutcome outcome,
        /** Score partiel BERTScore [0,1] des réponses ouvertes, null pour un QCM. */
        Double partialScore,
        String explanation
    ) {}
}
