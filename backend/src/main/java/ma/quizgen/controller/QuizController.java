package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.*;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.MoodleExportService;
import ma.quizgen.service.QuizService;
import ma.quizgen.service.ScormExportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/quizzes")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Création, gestion et export des quiz pédagogiques")
@SecurityRequirement(name = "bearerAuth")
public class QuizController {

    private final QuizService         quizService;
    private final ScormExportService  scormExportService;
    private final MoodleExportService moodleExportService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

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

    // ── Exports LMS ───────────────────────────────────────────────────────────

    /**
     * Génère et télécharge une archive ZIP SCORM 2004 (3rd Edition) du quiz.
     *
     * L'archive contient le manifest imsmanifest.xml, une page par question
     * avec tracking SCORM, et les ressources statiques nécessaires.
     *
     * Réservé aux enseignants propriétaires du quiz.
     */
    @GetMapping("/{id}/export/scorm")
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(
        summary = "Exporter le quiz au format SCORM 2004",
        description = "Génère une archive ZIP SCORM 2004 (3rd Edition) importable dans tout LMS compatible (Moodle, Canvas, Blackboard…)."
    )
    public ResponseEntity<byte[]> exportScorm(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        byte[] zipBytes = scormExportService.exportToScorm(id, CurrentUser.getKeycloakId(jwt));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("QuizGen-SCORM-" + id + ".zip", StandardCharsets.UTF_8)
            .build());
        headers.setContentLength(zipBytes.length);

        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }

    /**
     * Génère et télécharge un fichier XML au format Moodle Quiz Question Format.
     *
     * Importable dans Moodle 4.x via Administration du cours → Banque de questions → Importer.
     * Supporte QCM (multichoice), questions ouvertes (essay) et exercices (shortanswer).
     *
     * Réservé aux enseignants propriétaires du quiz.
     */
    @GetMapping("/{id}/export/moodle")
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(
        summary = "Exporter le quiz au format Moodle XML",
        description = "Génère un fichier XML Moodle Quiz Question Format importable dans Moodle 4.x."
    )
    public ResponseEntity<byte[]> exportMoodle(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        byte[] xmlBytes = moodleExportService.exportToMoodleXml(id, CurrentUser.getKeycloakId(jwt));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/xml; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("QuizGen-Moodle-" + id + ".xml", StandardCharsets.UTF_8)
            .build());
        headers.setContentLength(xmlBytes.length);

        return new ResponseEntity<>(xmlBytes, headers, HttpStatus.OK);
    }
}
