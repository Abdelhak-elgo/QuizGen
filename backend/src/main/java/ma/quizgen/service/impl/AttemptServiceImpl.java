package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SubmitAnswersRequest;
import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.AttemptService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttemptServiceImpl implements AttemptService {

    private final AttemptRepository  attemptRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository     userRepository;

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    @Override
    @Transactional
    public AttemptDto submit(UUID attemptId, SubmitAnswersRequest request, String studentKeycloakId) {
        User student = resolveUser(studentKeycloakId);
        Attempt attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tentative introuvable"));

        if (!attempt.getStudent().getId().equals(student.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");

        if (attempt.getAttemptStatus() == AttemptStatus.SUBMITTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tentative a déjà été soumise");

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(attempt.getSession().getEndTime())) {
            attempt.setAttemptStatus(AttemptStatus.EXPIRED);
            attemptRepository.save(attempt);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le temps de la session est écoulé");
        }

        // Score QCM : exact match (insensible à la casse + trim)
        // OUVERTE / EXERCICE : score 0 jusqu'à l'intégration BERTScore (Phase 5)
        List<Question> questions = questionRepository.findByQuiz_IdOrderByPositionAsc(
            attempt.getSession().getQuiz().getId());

        Map<String, String> answers = request.answers();
        int score = 0;
        for (Question q : questions) {
            if (q.getType() == QuestionType.QCM) {
                String given = answers.get(q.getId().toString());
                if (given != null && given.trim().equalsIgnoreCase(q.getCorrectAnswer().trim()))
                    score++;
            }
        }

        attempt.setAnswers(answers);
        attempt.setScore(score);
        attempt.setMaxScore(questions.size());
        attempt.setAttemptStatus(AttemptStatus.SUBMITTED);
        attempt.setCompletedAt(now);
        attempt = attemptRepository.save(attempt);

        log.info("Tentative {} soumise — score={}/{}", attemptId, score, questions.size());
        return AttemptDto.from(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptDto getById(UUID attemptId, String requesterKeycloakId) {
        User requester = resolveUser(requesterKeycloakId);
        Attempt attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tentative introuvable"));

        boolean isStudent = attempt.getStudent().getId().equals(requester.getId());
        boolean isTeacher = attempt.getSession().getQuiz().getTeacher().getId().equals(requester.getId());
        if (!isStudent && !isTeacher)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");

        return AttemptDto.from(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptDto> listForStudent(String studentKeycloakId, Pageable pageable) {
        User student = resolveUser(studentKeycloakId);
        Page<Attempt> page = attemptRepository.findByStudent_IdOrderByStartedAtDesc(student.getId(), pageable);
        return PageResponse.of(page.map(AttemptDto::from));
    }
}
