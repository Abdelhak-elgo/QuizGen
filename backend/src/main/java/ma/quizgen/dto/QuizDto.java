package ma.quizgen.dto;

import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuizStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record QuizDto(
    UUID id,
    UUID documentId,
    String documentName,
    UUID teacherId,
    String teacherName,
    String title,
    String description,
    Difficulty difficulty,
    Integer nbQuestions,
    QuizStatus quizStatus,
    String nlpTaskId,
    List<QuestionDto> questions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static QuizDto from(Quiz quiz) {
        return new QuizDto(
            quiz.getId(),
            quiz.getDocument().getId(),
            quiz.getDocument().getOriginalFilename(),
            quiz.getTeacher().getId(),
            quiz.getTeacher().getFirstName() + " " + quiz.getTeacher().getLastName(),
            quiz.getTitle(),
            quiz.getDescription(),
            quiz.getDifficulty(),
            quiz.getNbQuestions(),
            quiz.getQuizStatus(),
            quiz.getNlpTaskId(),
            quiz.getQuestions() == null ? List.of()
                : quiz.getQuestions().stream().map(QuestionDto::from).toList(),
            quiz.getCreatedAt(),
            quiz.getUpdatedAt()
        );
    }
}
