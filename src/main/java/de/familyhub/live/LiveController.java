package de.familyhub.live;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/live")
@Tag(name = "Live-Aktualisierung")
public class LiveController {

    private final LiveUpdates live;
    private final CurrentMember currentMember;

    public LiveController(LiveUpdates live, CurrentMember currentMember) {
        this.live = live;
        this.currentMember = currentMember;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Änderungen live mitbekommen",
            description = "Server-Sent Events: Nach jeder Änderung kommt das Ereignis \"aenderung\" mit dem Bereich, "
                    + "z. B. tasks, events, shopping, meals, redemptions. Inhalte kommen nicht mit; der Browser lädt "
                    + "den Bereich mit seinen eigenen Rechten neu.")
    public SseEmitter live() {
        currentMember.get();
        return live.connect();
    }
}
