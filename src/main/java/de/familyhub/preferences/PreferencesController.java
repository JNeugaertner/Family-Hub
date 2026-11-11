package de.familyhub.preferences;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// Eigene Anzeige-Einstellungen. Jede Person sieht und ändert nur ihre eigenen, auch Gäste und Kinder.
@RestController
@RequestMapping("/api/preferences")
@Tag(name = "Meine Anzeige")
public class PreferencesController {

    @Schema(description = "true: wird angezeigt. Ohne Einstellung ist alles an.")
    public record Preferences(
            @Schema(description = "Countdown bis zum nächsten Feiertag auf der Übersicht und im Kalender")
            @NotNull(message = "Bitte angeben, ob der Countdown angezeigt wird") Boolean holidayCountdown,
            @Schema(description = "Feiertage im Kalender an ihrem Tag markieren")
            @NotNull(message = "Bitte angeben, ob Feiertage im Kalender stehen") Boolean holidaysInCalendar) {

        static Preferences of(MemberPreferences stored) {
            return new Preferences(stored.showsHolidayCountdown(), stored.showsHolidaysInCalendar());
        }
    }

    private final MemberPreferencesRepository preferences;
    private final CurrentMember currentMember;

    public PreferencesController(MemberPreferencesRepository preferences, CurrentMember currentMember) {
        this.preferences = preferences;
        this.currentMember = currentMember;
    }

    @GetMapping
    @Operation(summary = "Eigene Anzeige-Einstellungen")
    public Preferences get() {
        FamilyMember viewer = currentMember.get();
        return Preferences.of(preferences.findById(viewer.id()).orElse(MemberPreferences.defaults(viewer.id())));
    }

    @PutMapping
    @Operation(summary = "Eigene Anzeige-Einstellungen ändern")
    public Preferences update(@Valid @RequestBody Preferences request) {
        FamilyMember viewer = currentMember.get();
        return Preferences.of(preferences.save(
                new MemberPreferences(viewer.id(), request.holidayCountdown(), request.holidaysInCalendar())));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eigene Anzeige-Einstellungen zurücksetzen", description = "Danach ist wieder alles an.")
    public void reset() {
        preferences.deleteById(currentMember.get().id());
    }
}
