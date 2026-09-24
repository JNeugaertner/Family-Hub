package de.familyhub.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ApiException extends ErrorResponseException {

    private ApiException(HttpStatus status, String title, String detail, Map<String, String> errors) {
        super(status, problem(status, title, detail, errors), null);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Nicht angemeldet", detail, null);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "Keine Berechtigung", detail, null);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Nicht gefunden", detail, null);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Konflikt", detail, null);
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiExceptionHandler.INVALID_INPUT_TITLE,
                ApiExceptionHandler.INVALID_INPUT_DETAIL, Map.of(field, message));
    }
}
