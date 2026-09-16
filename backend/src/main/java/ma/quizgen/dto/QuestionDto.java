package ma.quizgen.dto;

import ma.quizgen.entity.Question;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;

import java.util.List;
import java.util.UUID;

public record QuestionDto(
    UUID id,
    QuestionType type,
    String content,
    List<String> options,
    String correctAnswer,
    String explanation,
    Difficulty difficulty,
    List<String> keywords,
    Integer position
) {
    public static QuestionDto from(Question q) {
        return new QuestionDto(
            q.getId(),
            q.getType(),
            q.getContent(),
            q.getOptions(),
            q.getCorrectAnswer(),
            q.getExplanation(),
            q.getDifficulty(),
            q.getKeywords(),
            q.getPosition()
        );
    }
}
