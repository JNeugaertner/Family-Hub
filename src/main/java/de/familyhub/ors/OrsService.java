package de.familyhub.ors;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import de.familyhub.ors.OrsApi;
import de.familyhub.ors.OrsProperties;
import de.familyhub.web.ApiException;
import jakarta.annotation.PostConstruct;

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
            OrsApi.SearchResponse response = api.findPlaces(query);
            return response.features().stream()
                    .map(feature -> new OrsLocation(feature.properties().label(),
                            feature.geometry().coordinates().get(1),
                            feature.geometry().coordinates().get(0)))
                    .toList();
        }

 //Test ob API Aufruf funktioniert
@PostConstruct
public void test() {
    System.out.println("test");
}
}