package ma.quizgen.dto;

/**
 * Requête pour rejoindre une session.
 * Si la session a un code d'accès, il doit être fourni.
 */
public record SessionJoinRequest(
    String accessCode
) {}
