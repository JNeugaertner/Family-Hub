package de.familyhub.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Weist Anmeldungen für gesperrte Konten ab, bevor das Passwort geprüft wird (429 mit Retry-After).
// Bewusst keine Spring-Bean: sonst liefe der Filter zusätzlich außerhalb der Security-Kette.
class LoginThrottleFilter extends OncePerRequestFilter {

    static final String LOGIN_URL = "/api/auth/login";

    private final LoginAttempts attempts;
    private final ProblemResponses problems;

    LoginThrottleFilter(LoginAttempts attempts, ProblemResponses problems) {
        this.attempts = attempts;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(request.getContextPath() + LOGIN_URL).equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> locked = attempts.lockedFor(request.getParameter("username"));
        if (locked.isPresent()) {
            writeLocked(problems, request, response, locked.get());
            return;
        }
        chain.doFilter(request, response);
    }

    static void writeLocked(ProblemResponses problems, HttpServletRequest request, HttpServletResponse response,
            Duration remaining) throws IOException {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        response.setHeader("Retry-After", String.valueOf(remaining.toSeconds() + 1));
        problems.write(request, response, 429, "Zu viele Fehlversuche",
                "Zu viele falsche Passwörter für dieses Konto. Bitte in " + minutes
                        + (minutes == 1 ? " Minute" : " Minuten") + " erneut versuchen.");
    }
}
