package ma.quizgen.dto;

import ma.quizgen.entity.User;

import java.util.UUID;

/** Étudiant assigné à une session (vue enseignant). */
public record SessionParticipantDto(
    UUID id,
    String firstName,
    String lastName,
    String email
) {
    public static SessionParticipantDto from(User user) {
        return new SessionParticipantDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
