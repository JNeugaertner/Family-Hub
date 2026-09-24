package de.familyhub.web;

import java.util.Locale;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import de.familyhub.calendar.EventStatus;

// Query-Parameter in derselben Schreibweise wie im JSON annehmen, z. B. ?status=proposed.
@Configuration
public class EnumParameterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, EventStatus.class,
                value -> EventStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    }
}
