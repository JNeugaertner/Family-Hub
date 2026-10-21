package de.familyhub.notifications;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Eigene Benachrichtigungen an- und abschalten. Jede Person sieht und ändert nur ihre eigenen.
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Benachrichtigungen")
public class NotificationController {

    private final Notifications notifications;
    private final CurrentMember currentMember;

    public NotificationController(Notifications notifications, CurrentMember currentMember) {
        this.notifications = notifications;
        this.currentMember = currentMember;
    }

    // true: Benachrichtigungen dieser Art kommen an
    public record Settings(boolean tasks, boolean events, boolean confirmations, boolean messages) {

        static Settings of(Set<NotificationKind> enabled) {
            return new Settings(enabled.contains(NotificationKind.TASKS), enabled.contains(NotificationKind.EVENTS),
                    enabled.contains(NotificationKind.CONFIRMATIONS), enabled.contains(NotificationKind.MESSAGES));
        }

        Set<NotificationKind> enabled() {
            Set<NotificationKind> enabled = EnumSet.noneOf(NotificationKind.class);
            if (tasks) {
                enabled.add(NotificationKind.TASKS);
            }
            if (events) {
                enabled.add(NotificationKind.EVENTS);
            }
            if (confirmations) {
                enabled.add(NotificationKind.CONFIRMATIONS);
            }
            if (messages) {
                enabled.add(NotificationKind.MESSAGES);
            }
            return enabled;
        }
    }

    @GetMapping("/settings")
    @Operation(summary = "Eigene Benachrichtigungen",
            description = "tasks: dringende Aufgaben; events: kurzfristige Termine; confirmations: lange offene "
                    + "Bestätigungen (nur für alle, die bestätigen dürfen); messages: Chat-Nachrichten. "
                    + "Ohne Einstellung ist alles an.")
    public Settings settings() {
        return Settings.of(notifications.enabledFor(currentMember.get()));
    }

    @PutMapping("/settings")
    @Operation(summary = "Eigene Benachrichtigungen ändern")
    public Settings update(@RequestBody Settings settings) {
        FamilyMember viewer = currentMember.get();
        notifications.setEnabled(viewer, settings.enabled());
        return Settings.of(notifications.enabledFor(viewer));
    }
}
