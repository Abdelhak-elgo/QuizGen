package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.UserDto;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.Role;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @Override
    @Transactional
    public UserDto syncCurrentUser() {
        String keycloakId = currentUser.getKeycloakId();
        String email      = currentUser.getEmail();
        String firstName  = currentUser.getFirstName();
        String lastName   = currentUser.getLastName();
        Role   role       = resolveRole();

        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseGet(() -> {
                log.info("Nouveau utilisateur synchronisé depuis Keycloak: {}", email);
                return User.builder()
                    .keycloakId(keycloakId)
                    .email(email)
                    .build();
            });

        // Mise à jour systématique (les données peuvent changer dans Keycloak)
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setRole(role);

        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUserProfile() {
        String keycloakId = currentUser.getKeycloakId();
        return userRepository.findByKeycloakId(keycloakId)
            .map(this::toDto)
            .orElseThrow(() -> new RuntimeException(
                "Utilisateur non trouvé. Appelez d'abord POST /users/sync"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserDto> listAllUsers(Pageable pageable) {
        Page<UserDto> page = userRepository.findAll(pageable).map(this::toDto);
        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        return userRepository.findById(id)
            .map(this::toDto)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Role resolveRole() {
        if (currentUser.hasRole("ADMIN"))       return Role.ADMIN;
        if (currentUser.hasRole("ENSEIGNANT"))  return Role.ENSEIGNANT;
        return Role.ETUDIANT;
    }

    private UserDto toDto(User u) {
        return new UserDto(
            u.getId(),
            u.getKeycloakId(),
            u.getEmail(),
            u.getFirstName(),
            u.getLastName(),
            u.getRole(),
            u.getIsActive(),
            u.getCreatedAt()
        );
    }
}
