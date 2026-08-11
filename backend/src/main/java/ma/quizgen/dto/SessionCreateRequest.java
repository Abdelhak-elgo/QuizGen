package ma.quizgen.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionCreateRequest(

    @NotNull(message = "L'identifiant du quiz est obligatoire")
    UUID quizId,

    @NotNull(message = "La date de début est obligatoire")
    LocalDateTime startTime,

    @NotNull(message = "La date de fin est obligatoire")
    @Future(message = "La date de fin doit être dans le futur")
    LocalDateTime endTime,

    /** Code d'accès optionnel (null = session ouverte à tous). */
    @Size(min = 4, max = 20, message = "Le code d'accès doit contenir entre 4 et 20 caractères")
    String accessCode
) {}
