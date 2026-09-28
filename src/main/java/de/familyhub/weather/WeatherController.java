package de.familyhub.weather;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import de.familyhub.settings.FamilySettingsRepository;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// Wetter für den Wohnort der Familie (Entscheidungen vom 28.09.2026): Den Wohnort stellen Administratoren ein, sehen
// dürfen das Wetter alle, auch Gäste ("wetter/ansehen", bei Gästen mit dem Bereich "freigegeben").
@RestController
@RequestMapping("/api/weather")
@Tag(name = "Wetter")
public class WeatherController {

    private final WeatherService weather;
    private final FamilySettingsRepository settings;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public WeatherController(WeatherService weather, FamilySettingsRepository settings, CurrentMember currentMember,
            Permissions permissions) {
        this.weather = weather;
        this.settings = settings;
        this.currentMember = currentMember;
        this.permissions = permissions;
    }

    @GetMapping
    @Operation(summary = "Wetter am Wohnort", description = "Aktuell, heute und die nächsten vier Tage, dazu eine "
            + "Kleidungsempfehlung. 409, solange kein Wohnort eingestellt ist; 503, wenn OpenWeather nicht "
            + "eingerichtet oder nicht erreichbar ist.")
    public WeatherReport get() {
        permissions.require(currentMember.get(), Module.WETTER, Action.ANSEHEN, Scope.FREIGEGEBEN,
                "Keine Berechtigung, das Wetter anzusehen.");
        WeatherLocation location = settings.current().weatherLocation();
        if (location == null) {
            throw ApiException.conflict("Es ist noch kein Wohnort für das Wetter eingestellt.");
        }
        return weather.report(location);
    }

    @GetMapping("/places")
    @Operation(summary = "Ort suchen", description = "Nur für Administratoren. Bis zu fünf Treffer von OpenWeather.")
    public List<WeatherLocation> places(@RequestParam String q) {
        requireAdmin();
        if (q.isBlank()) {
            throw ApiException.invalidField("q", "Bitte einen Ort eingeben.");
        }
        return weather.findPlaces(q.strip());
    }

    @PutMapping("/location")
    @Operation(summary = "Wohnort einstellen", description = "Nur für Administratoren. Wert aus der Ortssuche.")
    public WeatherLocation setLocation(@Valid @RequestBody WeatherLocation location) {
        requireAdmin();
        settings.save(settings.current().withWeatherLocation(location));
        return location;
    }

    @DeleteMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Wohnort entfernen", description = "Nur für Administratoren. Danach zeigt die Übersicht kein "
            + "Wetter mehr.")
    public void removeLocation() {
        requireAdmin();
        settings.save(settings.current().withWeatherLocation(null));
    }

    private void requireAdmin() {
        FamilyMember viewer = currentMember.get();
        permissions.require(viewer, Module.SYSTEM, Action.VERWALTEN, Scope.FAMILIE,
                "Nur Administratoren dürfen den Wohnort einstellen.");
    }
}
