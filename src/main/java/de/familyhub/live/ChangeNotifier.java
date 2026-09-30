package de.familyhub.live;

import java.util.Optional;
import java.util.Set;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Meldet nach jeder erfolgreichen Änderung über die API den betroffenen Bereich, also das erste Pfadsegment nach
// /api/ (z. B. "tasks" für POST /api/tasks/{id}/confirm). So muss nicht jeder Controller selbst daran denken.
// Anmeldung und Passwort gehen niemanden sonst etwas an und werden nicht gemeldet.
class ChangeNotifier implements HandlerInterceptor {

    private static final Set<String> CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> NOT_ANNOUNCED = Set.of("auth", "live");

    private final LiveUpdates live;

    ChangeNotifier(LiveUpdates live) {
        this.live = live;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (ex != null || !CHANGING_METHODS.contains(request.getMethod()) || response.getStatus() >= 300) {
            return;
        }
        area(request.getRequestURI().substring(request.getContextPath().length())).ifPresent(live::publish);
    }

    static Optional<String> area(String path) {
        if (!path.startsWith("/api/")) {
            return Optional.empty();
        }
        String rest = path.substring("/api/".length());
        int slash = rest.indexOf('/');
        String area = slash < 0 ? rest : rest.substring(0, slash);
        return area.isEmpty() || NOT_ANNOUNCED.contains(area) ? Optional.empty() : Optional.of(area);
    }
}
