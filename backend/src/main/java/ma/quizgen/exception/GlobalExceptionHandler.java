package ma.quizgen.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Requête invalide");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex) {
        // Distinguish between "not found" and generic server errors
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("introuvable") || msg.contains("not found")) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            pd.setTitle("Ressource introuvable");
            pd.setDetail(msg);
            return pd;
        }
        log.error("Erreur interne: {}", msg, ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Erreur serveur");
        pd.setDetail(msg);
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Accès refusé");
        pd.setDetail("Vous n'avez pas les droits nécessaires pour effectuer cette action.");
        return pd;
    }

    @ExceptionHandler(InvalidBearerTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidBearerTokenException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Token invalide");
        pd.setDetail("Le token JWT est invalide ou expiré.");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Erreurs de validation");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setTitle("Fichier trop volumineux");
        pd.setDetail("La taille maximale autorisée est 50 Mo.");
        return pd;
    }
}
