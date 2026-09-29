package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.UserDto;
import ma.quizgen.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestion des utilisateurs QuizGen")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    /**
     * Synchronise l'utilisateur Keycloak dans la BDD.
     * À appeler une fois après le premier login (ou à chaque login pour mise à jour).
     */
    @PostMapping("/sync")
    @Operation(summary = "Synchroniser l'utilisateur Keycloak dans la BDD")
    public ResponseEntity<UserDto> syncUser() {
        return ResponseEntity.ok(userService.syncCurrentUser());
    }

    /**
     * Retourne le profil de l'utilisateur connecté.
     */
    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public ResponseEntity<UserDto> getMe() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    /**
     * Liste tous les utilisateurs — réservé ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister tous les utilisateurs (ADMIN)")
    public ResponseEntity<PageResponse<UserDto>> listUsers(
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();

        return ResponseEntity.ok(
            userService.listAllUsers(PageRequest.of(page, size, sort))
        );
    }

    /**
     * Étudiants actifs — pour que l'enseignant les assigne à une session.
     * Un étudiant n'apparaît qu'après sa première connexion (synchronisation Keycloak).
     */
    @GetMapping("/students")
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Lister les étudiants (assignation aux sessions)")
    public ResponseEntity<List<UserDto>> listStudents() {
        return ResponseEntity.ok(userService.listStudents());
    }

    /**
     * Retourne un utilisateur par son ID — réservé ADMIN.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Détail d'un utilisateur par ID (ADMIN)")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
