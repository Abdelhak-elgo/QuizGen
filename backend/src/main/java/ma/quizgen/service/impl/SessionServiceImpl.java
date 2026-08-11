package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SessionCreateRequest;
import ma.quizgen.dto.SessionDto;
import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.Session;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.QuizStatus;
import ma.quizgen.entity.enums.SessionStatus;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuizRepository;
import ma.quizgen.repository.SessionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.SessionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final QuizRepository    quizRepository;
    private final UserRepository    userRepository;
    private final AttemptRepository attemptRepository;

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Utilisateur introuvable"));
    }

    @Override
    @Transactional
    public SessionDto create(SessionCreateRequest request, String teacherKeycloakId) {
        User teacher = resolveUser(teacherKeycloakId);
        Quiz quiz = quizRepository.findById(request.quizId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));

        if (!quiz.getTeacher().getId().equals(teacher.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce quiz ne vous appartient pas");

        if (quiz.getQuizStatus() != QuizStatus.PUBLISHED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seuls les quiz publiés peuvent avoir des sessions");

        if (!request.endTime().isAfter(request.startTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin doit être postérieure au début");

        SessionStatus initial = LocalDateTime.now().isBefore(request.startTime())
            ? SessionStatus.SCHEDULED : SessionStatus.OPEN;

        Session session = sessionRepository.save(Session.builder()
            .quiz(quiz)
            .teacher(teacher)
            .startTime(request.startTime())
            .endTime(request.endTime())
            .accessCode(request.accessCode())
            .sessionStatus(initial)
            .build());

        log.info("Session {} créée pour quiz {}", session.getId(), quiz.getId());
        return SessionDto.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionDto getById(UUID sessionId) {
        return SessionDto.from(sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable")));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SessionDto> listForTeacher(String teacherKeycloakId, Pageable pageable) {
        User teacher = resolveUser(teacherKeycloakId);
        Page<Session> page = sessionRepository.findByTeacher_IdOrderByStartTimeDesc(teacher.getId(), pageable);
        return PageResponse.of(page.map(SessionDto::from));
    }

    @Override
    @Transactional
    public AttemptDto join(UUID sessionId, String accessCode, String studentKeycloakId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));

        LocalDateTime now = LocalDateTime.now();

        if (session.getSessionStatus() == SessionStatus.SCHEDULED && now.isBefore(session.getStartTime()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La session n'a pas encore démarré");

        if (session.getSessionStatus() == SessionStatus.CANCELLED
                || session.getSessionStatus() == SessionStatus.CLOSED
                || now.isAfter(session.getEndTime()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La session est terminée");

        if (session.getAccessCode() != null && !session.getAccessCode().isBlank()) {
            if (accessCode == null || !session.getAccessCode().equals(accessCode))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Code d'accès invalide");
        }

        User student = resolveUser(studentKeycloakId);

        // Idempotent — retourne l'attempt existant si l'étudiant a déjà rejoint
        var existing = attemptRepository.findBySession_IdAndStudent_Id(sessionId, student.getId());
        if (existing.isPresent()) return AttemptDto.from(existing.get());

        if (session.getSessionStatus() == SessionStatus.SCHEDULED) {
            session.setSessionStatus(SessionStatus.OPEN);
            sessionRepository.save(session);
        }

        Attempt attempt = attemptRepository.save(Attempt.builder()
            .session(session)
            .student(student)
            .maxScore(session.getQuiz().getQuestions().size())
            .attemptStatus(AttemptStatus.IN_PROGRESS)
            .build());

        log.info("Étudiant {} a rejoint session {} (attempt={})", student.getId(), sessionId, attempt.getId());
        return AttemptDto.from(attempt);
    }

    @Override
    @Transactional
    public void cancel(UUID sessionId, String teacherKeycloakId) {
        User teacher = resolveUser(teacherKeycloakId);
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));

        if (!session.getTeacher().getId().equals(teacher.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");

        if (session.getSessionStatus() == SessionStatus.CLOSED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible d'annuler une session clôturée");

        session.setSessionStatus(SessionStatus.CANCELLED);
        sessionRepository.save(session);
    }
}
