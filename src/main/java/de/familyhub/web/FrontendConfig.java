package de.familyhub.web;

import java.time.Duration;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Die gebaute Oberfläche liegt im Docker-Image unter static/ im JAR (siehe Dockerfile); Spring liefert sie unter /
// aus. Vite hängt an die Dateien unter assets/ einen Hash ihres Inhalts an, sie dürfen daher ein Jahr im Browser
// bleiben. index.html dagegen nicht (das regelt Spring Security mit "no-cache"), sonst sähe man nach einem Update
// weiter die alte Oberfläche.
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }

    // Tomcat kennt die Endung des App-Manifests nicht und schickte es sonst als application/octet-stream
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manifestMediaType() {
        return factory -> {
            MimeMappings mappings = new MimeMappings();
            mappings.add("webmanifest", "application/manifest+json");
            factory.addMimeMappings(mappings);
        };
    }
}
