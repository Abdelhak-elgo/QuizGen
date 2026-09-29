package ma.quizgen.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Remplace la liste des étudiants assignés à une session (liste vide = session ouverte). */
public record SessionParticipantsRequest(
    @NotNull(message = "La liste des étudiants est obligatoire (vide pour une session ouverte)")
    List<UUID> studentIds
) {}
