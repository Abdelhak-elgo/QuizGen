package ma.quizgen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Réponse du microservice FastAPI — GET /status/{task_id}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NlpTaskStatus(
    @JsonProperty("task_id")  String taskId,
    String status,
    Integer progress,
    List<Map<String, Object>> result,
    String error
) {
    public boolean isSuccess() { return "SUCCESS".equals(status); }
    public boolean isFailure() { return "FAILURE".equals(status); }
    public boolean isDone()    { return isSuccess() || isFailure(); }
}
