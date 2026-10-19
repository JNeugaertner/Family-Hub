package de.familyhub.ors;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import de.familyhub.ors.OrsApi;
import de.familyhub.ors.OrsProperties;
import de.familyhub.web.ApiException;

// Terminort
@Service
public class OrsService {
        private final OrsApi api;
    private final OrsProperties properties;

        public OrsService(OrsApi api, OrsProperties properties) {
        this.api = api;
        this.properties = properties;

        System.out.println(api.findPlaces("Anger 1 Erfurt"));
    }
}