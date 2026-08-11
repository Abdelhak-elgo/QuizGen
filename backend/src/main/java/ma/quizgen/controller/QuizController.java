package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.*;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.QuizService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/quizzes")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Création et gestion des quiz pédagogiques")
@SecurityRequirement(name = "bearerAuth")
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Créer un quiz — déclenche la génération NLP via Mistral")
    public QuizDto create(
            @Valid @RequestBody QuizCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return quizService.create(request, CurrentUser.getKeycloakId(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un quiz avec ses questions")
    public QuizDto getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return quizService.getById(id, CurrentUser.getKeycloakId(jwt));
    }

    @GetMapping
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Liste paginée des quiz de l'enseignant connecté")
    public PageResponse<QuizDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return quizService.listForTeacher(
            CurrentUser.getKeycloakId(jwt),
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @PutMapping("/{id}/questions")
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Éditer / valider les questions générées. Passer publish=true pour publier.")
    public QuizDto updateQuestions(
            @PathVariable UUID id,
            @Valid @RequestBody QuizUpdateQuestionsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return quizService.updateQuestions(id, request, CurrentUser.getKeycloakId(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Supprimer un quiz et ses questions")
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        quizService.delete(id, CurrentUser.getKeycloakId(jwt));
    }
}
