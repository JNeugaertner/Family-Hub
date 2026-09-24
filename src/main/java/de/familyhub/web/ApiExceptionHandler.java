package de.familyhub.web;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;

// Alle Fehler als ProblemDetail (RFC 9457); Feldfehler zusätzlich unter "errors": { feld: meldung }.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String INVALID_INPUT_TITLE = "Ungültige Eingaben";
    static final String INVALID_INPUT_DETAIL = "Mindestens ein Feld ist ungültig, Details unter \"errors\".";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_INPUT_DETAIL);
        problem.setTitle(INVALID_INPUT_TITLE);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Die Anfrage enthält ungültiges JSON oder einen Wert im falschen Format, z. B. eine unbekannte "
                        + "Kategorie oder Rolle oder ein Datum, das nicht dem Format 2026-09-25T10:00 entspricht.");
        problem.setTitle("Anfrage nicht lesbar");
        fieldOf(ex).ifPresent(field -> problem.setProperty("errors", Map.of(field, "Ungültiger oder fehlender Wert")));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Jackson hängt den Pfad zum fehlerhaften Feld an seine Ausnahme; das letzte Element ist der Feldname.
    private static Optional<String> fieldOf(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                return Optional.ofNullable(jackson.getPath().getLast().getPropertyName());
            }
        }
        return Optional.empty();
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Der Wert \"" + ex.getValue() + "\" für \"" + ex.getPropertyName() + "\" hat ein ungültiges Format.");
        problem.setTitle(INVALID_INPUT_TITLE);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }
}
