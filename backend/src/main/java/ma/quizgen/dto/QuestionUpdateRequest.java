package ma.quizgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;

import java.util.List;
import java.util.UUID;

public record QuestionUpdateRequest(

    /** null = nouvelle question (sans id existant). */
    UUID id,

    @NotNull QuestionType type,

    @NotBlank @Size(max = 2000) String content,

    /** Exactement 4 éléments pour QCM, null sinon. */
    List<String> options,

    @NotBlank @Size(max = 2000) String correctAnswer,

    @Size(max = 2000) String explanation,

    @NotNull Difficulty difficulty,

    List<String> keywords,

    Integer position
) {}
