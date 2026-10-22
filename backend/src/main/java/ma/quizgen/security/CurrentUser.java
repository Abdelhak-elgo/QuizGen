package ma.quizgen.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Helper to access authenticated user information from the Keycloak JWT.
 * Provides both instance methods (via SecurityContextHolder) and
 * static convenience methods (from a Jwt parameter injected in controllers).
 */
@Component
public class CurrentUser {

    // ── Instance methods (SecurityContextHolder) ──────────────────────────────

    public Jwt getJwt() {
        return (Jwt) SecurityContextHolder.getContext()
            .getAuthentication()
            .getPrincipal();
    }

    public String getEmail() {
        return getJwt().getClaimAsString("email");
    }

    public String getFirstName() {
        return getJwt().getClaimAsString("given_name");
    }

    public String getLastName() {
        return getJwt().getClaimAsString("family_name");
    }

    public String getKeycloakId() {
        return getJwt().getSubject();
    }

    public boolean hasRole(String role) {
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    // ── Static helpers (controller parameter injection) ───────────────────────

    /**
     * Extrait le Keycloak user ID (claim "sub") depuis un Jwt injecté par Spring Security.
     * Utilisé dans les contrôleurs pour passer l'identité aux services sans DI circulaire.
     */
    public static String getKeycloakId(Jwt jwt) {
        return jwt.getSubject();
    }

    /** Vrai si l'utilisateur authentifié porte le rôle ADMIN (vue globale de la plateforme). */
    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
