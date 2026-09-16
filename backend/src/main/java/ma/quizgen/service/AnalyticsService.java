package ma.quizgen.service;

import ma.quizgen.dto.QuizAnalyticsDto;
import ma.quizgen.dto.StudentDashboardDto;

import java.util.UUID;

public interface AnalyticsService {

    QuizAnalyticsDto getQuizAnalytics(UUID quizId, String teacherKeycloakId);

    StudentDashboardDto getStudentDashboard(String studentKeycloakId);
}
