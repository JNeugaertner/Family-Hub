package de.familyhub.ors;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import org.springframework.web.client.ResourceAccessException;
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
        try{
        return rest.get()
                .uri(properties.baseUrl() + "/geocode/search?text="
                + URLEncoder.encode(query, StandardCharsets.UTF_8))
                .header("Authorization", properties.apiKey())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {throw apiError(response);})  
                .body(SearchResponse.class);
        }catch (ResourceAccessException e){throw new OrsException("OpenRouteService ist nicht erreichbar.");}}

    public RouteResponse gettravelTime(String transportMode, double startLat, double startLon, double endLat, double endLon) {
        try{
        return rest.get()
                .uri(properties.baseUrl() + "/v2/directions/" + transportMode + "?start="+startLon+","+startLat+"&end="+endLon+","+endLat)
                .header("Authorization", properties.apiKey())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {throw apiError(response);})                
                .body(RouteResponse.class);
        }catch (ResourceAccessException e){throw new OrsException("OpenRouteService ist nicht erreichbar.");}}

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

    //Fehlerbehandlung
    private static OrsException apiError(ClientHttpResponse response) throws IOException{
        int status = response.getStatusCode().value();
                String detail = new String(response.getBody().readAllBytes(), UTF_8).replaceAll("\\s+", " ").strip();
                return new OrsException("OpenRouteService hat die Anfrage abgelehnt (HTTP " + status + "): "
                + (detail.length() > 200 ? detail.substring(0, 200) + "…" : detail));
    }
}
