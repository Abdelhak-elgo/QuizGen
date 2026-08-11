package ma.quizgen.service;

import ma.quizgen.dto.UserDto;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.Role;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl — tests unitaires")
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private CurrentUser    currentUser;

    @InjectMocks private UserServiceImpl userService;

    private static final String KEYCLOAK_ID = "kc-uuid-123";
    private static final String EMAIL       = "prof.ahmed@univ.ma";
    private static final String FIRST_NAME  = "Ahmed";
    private static final String LAST_NAME   = "Benali";

    @BeforeEach
    void setupCurrentUser() {
        when(currentUser.getKeycloakId()).thenReturn(KEYCLOAK_ID);
        when(currentUser.getEmail()).thenReturn(EMAIL);
        when(currentUser.getFirstName()).thenReturn(FIRST_NAME);
        when(currentUser.getLastName()).thenReturn(LAST_NAME);
    }

    // ── syncCurrentUser ───────────────────────────────────────────────────────

    @Test
    @DisplayName("syncCurrentUser — crée un nouvel utilisateur s'il n'existe pas")
    void syncCurrentUser_shouldCreateNewUser_whenNotFound() {
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.hasRole("ENSEIGNANT")).thenReturn(true);
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserDto result = userService.syncCurrentUser();

        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.firstName()).isEqualTo(FIRST_NAME);
        assertThat(result.role()).isEqualTo(Role.ENSEIGNANT);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("syncCurrentUser — met à jour un utilisateur existant")
    void syncCurrentUser_shouldUpdateExistingUser() {
        User existing = User.builder()
            .id(UUID.randomUUID())
            .keycloakId(KEYCLOAK_ID)
            .email(EMAIL)
            .firstName("Ancien")
            .lastName("Nom")
            .role(Role.ETUDIANT)
            .build();

        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.hasRole("ENSEIGNANT")).thenReturn(true);
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto result = userService.syncCurrentUser();

        assertThat(result.firstName()).isEqualTo(FIRST_NAME);
        assertThat(result.role()).isEqualTo(Role.ENSEIGNANT);
        verify(userRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("syncCurrentUser — attribue ADMIN si rôle ADMIN présent dans JWT")
    void syncCurrentUser_shouldAssignAdminRole() {
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserDto result = userService.syncCurrentUser();

        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }

    // ── getCurrentUserProfile ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentUserProfile — retourne le profil si l'utilisateur existe")
    void getCurrentUserProfile_shouldReturnProfile_whenUserExists() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .keycloakId(KEYCLOAK_ID)
            .email(EMAIL)
            .role(Role.ENSEIGNANT)
            .build();

        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));

        UserDto result = userService.getCurrentUserProfile();

        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("getCurrentUserProfile — lève une exception si non synchronisé")
    void getCurrentUserProfile_shouldThrow_whenNotSynced() {
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUserProfile())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("sync");
    }

    // ── listAllUsers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAllUsers — retourne une page paginée")
    void listAllUsers_shouldReturnPage() {
        User u = User.builder().id(UUID.randomUUID()).email(EMAIL).role(Role.ETUDIANT).build();
        when(userRepository.findAll(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(u)));

        var result = userService.listAllUsers(PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById — lève RuntimeException si introuvable")
    void getUserById_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("introuvable");
    }
}
