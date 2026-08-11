package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.QuizAnalyticsDto;
import ma.quizgen.dto.StudentDashboardDto;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Statistiques quiz et tableau de bord étudiant")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/quiz/{quizId}")
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    @Operation(summary = "Stats d'un quiz : score moyen, taux de réussite, stats par question")
    public QuizAnalyticsDto quizAnalytics(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal Jwt jwt) {
        return analyticsService.getQuizAnalytics(quizId, CurrentUser.getKeycloakId(jwt));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Tableau de bord de l'étudiant : scores et historique")
    public StudentDashboardDto studentDashboard(
            @AuthenticationPrincipal Jwt jwt) {
        return analyticsService.getStudentDashboard(CurrentUser.getKeycloakId(jwt));
    }
}
