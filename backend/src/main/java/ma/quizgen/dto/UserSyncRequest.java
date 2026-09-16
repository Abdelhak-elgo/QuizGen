package ma.quizgen.dto;

/**
 * Payload optionnel pour POST /users/sync.
 * En pratique, toutes les données viennent du JWT Keycloak.
 * Ce record permet d'étendre le contrat si nécessaire.
 */
public record UserSyncRequest() {}
