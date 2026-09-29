package ma.quizgen.service;

import ma.quizgen.dto.AssignedSessionDto;
import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SessionCreateRequest;
import ma.quizgen.dto.SessionDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SessionService {

    SessionDto create(SessionCreateRequest request, String teacherKeycloakId);

    SessionDto getById(UUID sessionId);

    PageResponse<SessionDto> listForTeacher(String teacherKeycloakId, Pageable pageable);

    /**
     * Rejoint la session et crée un Attempt. Session avec participants : réservée
     * aux étudiants assignés (sans code) ; sinon, code d'accès éventuel requis.
     */
    AttemptDto join(UUID sessionId, String accessCode, String studentKeycloakId);

    void cancel(UUID sessionId, String teacherKeycloakId);

    /** Remplace les étudiants assignés à une session (liste vide = session ouverte). */
    SessionDto updateParticipants(UUID sessionId, List<UUID> studentIds, String teacherKeycloakId);

    /** Sessions auxquelles l'étudiant connecté est assigné, avec l'état de sa tentative. */
    List<AssignedSessionDto> listAssignedToStudent(String studentKeycloakId);
}
