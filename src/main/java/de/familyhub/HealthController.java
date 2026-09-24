package de.familyhub;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "System")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Prüfen, ob das Backend läuft")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "FamilyHub");
    }
}
