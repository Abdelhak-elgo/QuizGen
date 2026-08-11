package ma.quizgen.dto;

import jakarta.validation.constraints.*;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;

import java.util.List;
import java.util.UUID;

public record QuizCreateRequest(

    @NotNull(message = "L'identifiant du document est obligatoire")
    UUID documentId,

    @NotBlank(message = "Le titre est obligatoire")
    @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
    String title,

    @Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères")
    String description,

    @NotNull(message = "La difficulté est obligatoire")
    Difficulty difficulty,

    @NotNull(message = "Le nombre de questions est obligatoire")
    @Min(value = 3, message = "Minimum 3 questions")
    @Max(value = 25, message = "Maximum 25 questions")
    Integer nbQuestions,

    /** Types de questions souhaités (défaut : QCM + OUVERTE). */
    List<QuestionType> questionTypes
) {}
