package de.familyhub.ors;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                .uri(properties.baseUrl() + "/geocode/search?text=" + query)
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
            if (status == 400) {
                String detail = new String(response.getBody().readAllBytes(), UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(detail);
                int code = root.path("error").path("code").asInt();
                if (code == 1001){
                    return new OrsException("Pflichtparameter fehlt, gib einen Suchbegriff ein");}
                if (code == 1003){
                    return new OrsException("Ungültiger Suchbegriff");}
                if (code == 1008){
                    return new OrsException("gib einen Suchbegriff ein");}
                if (code == 1009){
                    return new OrsException("keine Suchergebnisse gefunden");}
                if (code == 2001){
                    return new OrsException("Pflichtparameter fehlt, gib Transportmittel, Terminort und Wohnadresse an");}
                if (code ==2004){ 
                    return new OrsException("Entfernung zum Zielort ist zu groß");}
                if (code == 2009){
                    return new OrsException("keine Route gefunden");}
                if (code == 2010){
                    return new OrsException("Einer der Orte konnte nicht gefunden werden");}
                if (code == 2016){
                    return new OrsException("keine Route gefunden");}

                return new OrsException("ungültige Anfrage");}

            if (status == 401) {
                return new OrsException("ungültiger API-Key");}
            if (status == 403) {
                return new OrsException("Zugriff verweigert");}
            if (status == 404) {
                return new OrsException("Endpoint nicht gefunden");}
            if (status == 413) {
                return new OrsException("Anfrage zu groß");}
            if (status == 429) {
                return new OrsException("Anfrage-Limit erreicht");}
            if (status == 500) {
                return new OrsException("Serverfehler");}
            if (status == 503) {
                return new OrsException("OpenRouteService ist momentan nicht verfügbar");}

                String detail = new String(response.getBody().readAllBytes(), UTF_8).replaceAll("\\s+", " ").strip();
                return new OrsException("OpenRouteService hat die Anfrage abgelehnt (HTTP " + status + "): "
                + (detail.length() > 200 ? detail.substring(0, 200) + "…" : detail));
    }
}
