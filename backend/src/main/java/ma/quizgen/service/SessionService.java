package ma.quizgen.service;

import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SessionCreateRequest;
import ma.quizgen.dto.SessionDto;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SessionService {

    SessionDto create(SessionCreateRequest request, String teacherKeycloakId);

    SessionDto getById(UUID sessionId);

    PageResponse<SessionDto> listForTeacher(String teacherKeycloakId, Pageable pageable);

    /** Rejoint la session (vérifie le code, crée un Attempt). */
    AttemptDto join(UUID sessionId, String accessCode, String studentKeycloakId);

    void cancel(UUID sessionId, String teacherKeycloakId);
}
