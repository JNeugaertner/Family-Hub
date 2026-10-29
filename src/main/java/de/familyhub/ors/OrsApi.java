package de.familyhub.ors;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.URLEncoder;

@Component
public class OrsApi {
    private final OrsProperties properties;
    private final RestClient rest;

    public OrsApi(OrsProperties properties) {
        this.properties = properties;
        this.rest = RestClient.builder().build();
    }
    public SearchResponse findPlaces(String query) {
        return rest.get()
                .uri(properties.baseUrl() + "/geocode/search?text="
                + URLEncoder.encode(query, StandardCharsets.UTF_8))
                .header("Authorization", properties.apiKey())
                .retrieve()
                .body(SearchResponse.class);
    }

    public RouteResponse gettravelTime(String transportMode, double startLat, double startLon, double endLat, double endLon) {
        return rest.get()
                .uri(properties.baseUrl() + "/v2/directions/" + transportMode + "?start="+startLon+","+startLat+"&end="+endLon+","+endLat)
                .header("Authorization", properties.apiKey())
                .retrieve()
                .body(RouteResponse.class);
    }

    record SearchResponse(List<Feature> features) {
    }
    record Feature(Geometry geometry, FeatureProperties properties) {
    }
    record Geometry(List<Double> coordinates) {
    }
    record FeatureProperties(String label) {
    }


    record RouteResponse(List<RouteFeature> features) {
    }
    record RouteFeature(RouteProperties properties) {
    }
    record RouteProperties(RouteSummary summary) {
    }
    record RouteSummary(double distance, double duration) {
    }
}
