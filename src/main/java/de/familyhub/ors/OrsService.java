package de.familyhub.ors;

import java.util.List;

import org.springframework.stereotype.Service;

import de.familyhub.web.ApiException;

// Terminort
@Service
public class OrsService {
        private final OrsApi api;
        private final OrsProperties properties;

        public OrsService(OrsApi api, OrsProperties properties) {
        this.api = api;
        this.properties = properties;

        }
        public List<OrsLocation> findPlaces(String query) {
            requireConfigured();
            OrsApi.SearchResponse response = api.findPlaces(query);
            return response.features().stream()
                    .map(feature -> new OrsLocation(feature.properties().label(),
                            feature.geometry().coordinates().get(1),
                            feature.geometry().coordinates().get(0)))
                    .toList();
        }

        public double gettravelTime(String transportMode, double startLat, double startLon, double endLat, double endLon) {
            requireConfigured();
            OrsApi.RouteResponse response = api.gettravelTime(transportMode, startLat, startLon, endLat, endLon);
            return response.features().get(0).properties().summary().duration();
        }

        private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw ApiException.unavailable("Die Fahrzeitberechnung ist nicht eingerichtet: Es fehlt der OpenRouteService-API-Schlüssel "
                    + "(familyhub.ors.api-key in local.properties, siehe README).");
        }
    }
}