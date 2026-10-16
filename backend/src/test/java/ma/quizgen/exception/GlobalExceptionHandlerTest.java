package ma.quizgen.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler — statuts HTTP renvoyés au client")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResponseStatusException : conserve le statut (409) et le message métier")
    void responseStatusException_keepsStatusAndReason() {
        ProblemDetail pd = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.CONFLICT, "Ce document est utilisé par un quiz"));

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).isEqualTo("Ce document est utilisé par un quiz");
    }

    @Test
    @DisplayName("ResponseStatusException 403 reste un 403 (et non un 500)")
    void forbidden_staysForbidden() {
        ProblemDetail pd = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès à ce quiz non autorisé"));

        assertThat(pd.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Erreur inattendue : 500 sans exposer le message interne")
    void unexpectedRuntime_hidesInternalMessage() {
        ProblemDetail pd = handler.handleRuntime(
            new IllegalStateException("could not execute statement; SQL [delete from documents]"));

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("SQL");
    }

    @Test
    @DisplayName("RuntimeException « introuvable » : 404 avec le message")
    void notFoundRuntime_is404() {
        ProblemDetail pd = handler.handleRuntime(new RuntimeException("Document introuvable"));

        assertThat(pd.getStatus()).isEqualTo(404);
        assertThat(pd.getDetail()).isEqualTo("Document introuvable");
    }
}
