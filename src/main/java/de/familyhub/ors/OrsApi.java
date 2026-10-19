package de.familyhub.ors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrsApi {
    private final OrsProperties properties;
    private final RestClient rest;

    public OrsApi(OrsProperties properties) {
        this.properties = properties;
        this.rest = RestClient.builder().build();
    }
    public String findPlaces(String query) {
        return rest.get()
                .uri(properties.baseUrl() + "/geocode/search?text="
                +query.replace(" ","%20"))
                .header("Authorization", properties.apiKey())
                .retrieve()
                .body(String.class);
    }

}
