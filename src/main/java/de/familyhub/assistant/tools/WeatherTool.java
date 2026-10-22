package de.familyhub.assistant.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;
import de.familyhub.settings.FamilySettingsRepository;
import de.familyhub.weather.WeatherException;
import de.familyhub.weather.WeatherLocation;
import de.familyhub.weather.WeatherService;

// Wetter am Wohnort, dasselbe wie die Wetter-Kachel in der Übersicht
@Component
public class WeatherTool implements AssistantTool {

    static final String NAME = "wetter_lesen";

    private final WeatherService weather;
    private final FamilySettingsRepository settings;
    private final AgentRights rights;

    public WeatherTool(WeatherService weather, FamilySettingsRepository settings, AgentRights rights) {
        this.weather = weather;
        this.settings = settings;
        this.rights = rights;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Liest das Wetter am Wohnort der Familie: jetzt, heute und die nächsten vier Tage, "
                + "dazu eine Kleidungsempfehlung. Temperaturen in °C, Wind in km/h.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
    }

    @Override
    public String label() {
        return "Wetter";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.WETTER, Scope.FREIGEGEBEN);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        WeatherLocation location = settings.current().weatherLocation();
        if (location == null) {
            throw new ToolException("Es ist noch kein Wohnort für das Wetter eingestellt.");
        }
        try {
            return weather.report(location);
        } catch (WeatherException e) {
            throw new ToolException(e.getMessage());
        }
    }
}
