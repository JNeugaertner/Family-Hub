package de.familyhub.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

// Die Zugangsdaten kommen über application.properties aus den Umgebungsvariablen. Ohne sie ist die Anbindung nicht
// eingerichtet; früher stand dann der Platzhalter selbst im Feld und alles galt als eingerichtet.
class WhatsAppPropertiesTest {

    // Bindet wie beim Start: die echte application.properties, dazu die angegebenen Umgebungsvariablen
    private static WhatsAppProperties bindWith(Map<String, Object> environment) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("umgebung", environment));
        sources.addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
        return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
                .bindOrCreate("familyhub.whatsapp", WhatsAppProperties.class);
    }

    @Test
    void withoutEnvironmentVariablesNothingIsConfigured() throws IOException {
        WhatsAppProperties properties = bindWith(Map.of());

        assertThat(properties.accessToken()).isEmpty();
        assertThat(properties.verifyToken()).isEmpty();
        assertThat(properties.appSecret()).isEmpty();
        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.apiBaseUrl()).startsWith("https://graph.facebook.com/");
    }

    @Test
    void environmentVariablesAreRead() throws IOException {
        WhatsAppProperties properties = bindWith(Map.of(
                "WHATSAPP_ACCESS_TOKEN", "token",
                "WHATSAPP_PHONE_NUMBER_ID", "12345",
                "WHATSAPP_VERIFY_TOKEN", "verify",
                "WHATSAPP_APP_SECRET", "secret"));

        assertThat(properties.accessToken()).isEqualTo("token");
        assertThat(properties.phoneNumberId()).isEqualTo("12345");
        assertThat(properties.verifyToken()).isEqualTo("verify");
        assertThat(properties.appSecret()).isEqualTo("secret");
        assertThat(properties.isConfigured()).isTrue();
    }
}
