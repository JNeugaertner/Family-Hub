package de.familyhub.ors;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import de.familyhub.weather.WeatherLocation;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import de.familyhub.settings.FamilySettingsRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RequestParam;


@RestController 
@RequestMapping ("/api/ors")
public class OrsController {

    private final OrsService service;
    private final FamilySettingsRepository settings;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public OrsController(OrsService service, FamilySettingsRepository settings, CurrentMember currentMember, Permissions permissions) {
        this.service = service;
        this.settings = settings;
        this.currentMember = currentMember;
        this.permissions = permissions;
    }

    @GetMapping 
    @Operation(summary = "Terminort", description = "Der Ort des Termins, der in der App angezeigt wird.")
    public List<OrsLocation> get(@RequestParam String q) {
        if (q.isBlank()) {
            throw ApiException.invalidField("q", "Bitte einen Ort eingeben.");
        }
        return service.findPlaces(q);
    }

    @GetMapping("/travelTime")
    @Operation (summary = "Reisezeit", description = "Die Reisezeit zwischen zwei Orten in Sekunden.")
    public double travelTime(@RequestParam String transportMode, @RequestParam double startLat, @RequestParam double startLon, @RequestParam double endLat, @RequestParam double endLon) {
        return service.gettravelTime(transportMode, startLat, startLon, endLat, endLon);
    }

        @PutMapping("/location")
    @Operation(summary = "Wohnort einstellen", description = "Nur für Administratoren. Wert aus der Ortssuche.")
    public OrsLocation setLocation(@Valid @RequestBody OrsLocation location) {
        requireAdmin();
        settings.save(settings.current().withHomeLocation(location));
        return location;
    }


        private void requireAdmin() {
        FamilyMember viewer = currentMember.get();
        permissions.require(viewer, Module.SYSTEM, Action.VERWALTEN, Scope.FAMILIE,
                "Nur Administratoren dürfen den Wohnort einstellen.");
    }


}
