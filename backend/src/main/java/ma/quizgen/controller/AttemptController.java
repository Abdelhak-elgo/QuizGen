package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SubmitAnswersRequest;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.AttemptService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/attempts")
@RequiredArgsConstructor
@Tag(name = "Attempts", description = "Soumission et résultats des tentatives étudiant")
@SecurityRequirement(name = "bearerAuth")
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Soumettre les réponses — calcul du score QCM automatique")
    public AttemptDto submit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitAnswersRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return attemptService.submit(id, request, CurrentUser.getKeycloakId(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une tentative (étudiant propriétaire ou enseignant du quiz)")
    public AttemptDto getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return attemptService.getById(id, CurrentUser.getKeycloakId(jwt));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Historique des tentatives de l'étudiant connecté")
    public PageResponse<AttemptDto> myAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return attemptService.listForStudent(
            CurrentUser.getKeycloakId(jwt),
            PageRequest.of(page, size, Sort.by("startedAt").descending()));
    }
}
