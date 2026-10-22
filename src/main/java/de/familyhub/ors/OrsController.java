package de.familyhub.ors;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RequestParam;


@RestController 
@RequestMapping ("/api/ors")
public class OrsController {

    private final OrsService service;

    public OrsController(OrsService service) {
        this.service = service;
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
    public double travelTime(@RequestParam double startLat, @RequestParam double startLon, @RequestParam double endLat, @RequestParam double endLon) {
        return service.gettravelTime(startLat, startLon, endLat, endLon);
    }


}
