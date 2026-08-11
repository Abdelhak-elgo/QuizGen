package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.auth.AuthResponse;
import ma.quizgen.dto.auth.LoginRequest;
import ma.quizgen.dto.auth.RegisterRequest;
import ma.quizgen.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription et connexion via Keycloak")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Inscription d'un nouvel utilisateur dans Keycloak")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Vérification des identifiants et récupération du token (via Keycloak)")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Note: The actual login should be handled by Keycloak's token endpoint
        // This endpoint is just for backward compatibility
        return ResponseEntity.ok(authService.login(request));
    }
}