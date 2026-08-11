package ma.quizgen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record QuizUpdateQuestionsRequest(

    @NotEmpty(message = "La liste de questions ne peut pas être vide")
    @Valid
    List<QuestionUpdateRequest> questions,

    /** Si true, publie le quiz après la mise à jour. */
    boolean publish
) {}
