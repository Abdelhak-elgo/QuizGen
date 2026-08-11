package ma.quizgen.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Helper to access authenticated user information from the Keycloak JWT.
 */
@Component
public class CurrentUser {

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
}
