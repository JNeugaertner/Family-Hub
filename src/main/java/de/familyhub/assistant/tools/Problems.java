package de.familyhub.assistant.tools;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ProblemDetail;

import de.familyhub.web.ApiException;

// Die Meldung einer ApiException, wie sie die Oberfläche zeigen würde: bei Feldfehlern die Meldungen der Felder,
// sonst der Text (detail). Für das Modell und für Entwürfe, die sich nicht übernehmen lassen.
public final class Problems {

    private Problems() {
    }

    public static String message(RuntimeException e) {
        if (!(e instanceof ApiException api)) {
            return e.getMessage();
        }
        ProblemDetail body = api.getBody();
        if (body.getProperties() != null && body.getProperties().get("errors") instanceof Map<?, ?> errors
                && !errors.isEmpty()) {
            return errors.values().stream().map(String::valueOf).collect(Collectors.joining("; "));
        }
        return body.getDetail();
    }
}
