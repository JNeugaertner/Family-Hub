package de.familyhub.live;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

// Live-Aktualisierung (Entscheidung vom 30.09.2026): Jeder angemeldete Browser hält eine Verbindung (Server-Sent
// Events). Ändert sich etwas, erfahren alle nur den Bereich, z. B. "tasks", und laden ihn mit ihren eigenen Rechten
// neu. Inhalte gehen nie mit, so sieht niemand etwas, das er nicht sehen darf (z. B. private Termine).
// Die Verbindungen liegen im Speicher dieses Backends; bei mehreren Backend-Instanzen bräuchte es einen Vermittler.
@Component
public class LiveUpdates {

    static final String EVENT_NAME = "aenderung";
    // Danach baut der Browser die Verbindung von selbst neu auf
    static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(30);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitters.remove(emitter));
        // Erste Zeile sofort senden, damit der Browser die Verbindung als offen meldet
        send(emitter, SseEmitter.event().comment("verbunden"));
        return emitter;
    }

    // area: erstes Pfadsegment nach /api/, z. B. "tasks" oder "shopping"
    public void publish(String area) {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().name(EVENT_NAME).data(area)));
    }

    // Hält stille Verbindungen offen und räumt abgebrochene auf
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    int connectionCount() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // Browser hat die Verbindung geschlossen
            emitters.remove(emitter);
        }
    }
}
