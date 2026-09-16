package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.*;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.SessionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Gestion des sessions d'examen")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Créer une session d'examen pour un quiz publié")
    public SessionDto create(
            @Valid @RequestBody SessionCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return sessionService.create(request, CurrentUser.getKeycloakId(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une session")
    public SessionDto getById(@PathVariable UUID id) {
        return sessionService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Liste paginée des sessions de l'enseignant connecté")
    public PageResponse<SessionDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return sessionService.listForTeacher(
            CurrentUser.getKeycloakId(jwt),
            PageRequest.of(page, size, Sort.by("startTime").descending()));
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Rejoindre une session — crée une Attempt pour l'étudiant")
    public AttemptDto join(
            @PathVariable UUID id,
            @RequestBody(required = false) SessionJoinRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String code = (request != null) ? request.accessCode() : null;
        return sessionService.join(id, code, CurrentUser.getKeycloakId(jwt));
    }

    @DeleteMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Annuler une session")
    public void cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        sessionService.cancel(id, CurrentUser.getKeycloakId(jwt));
    }
}
