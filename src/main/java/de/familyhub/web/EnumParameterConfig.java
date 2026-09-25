package de.familyhub.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.familyhub.calendar.EventStatus;
import de.familyhub.task.TaskStatus;

// Query-Parameter in derselben Schreibweise wie im JSON annehmen, z. B. ?status=proposed oder ?status=inprogress.
@Configuration
public class EnumParameterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, EventStatus.class, value -> byJsonName(EventStatus.class, value));
        registry.addConverter(String.class, TaskStatus.class, value -> byJsonName(TaskStatus.class, value));
    }

    private static <E extends Enum<E>> E byJsonName(Class<E> type, String value) {
        String wanted = value.trim();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(wanted) || jsonName(type, constant).equalsIgnoreCase(wanted)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unbekannter Wert: " + value);
    }

    private static String jsonName(Class<?> type, Enum<?> constant) {
        try {
            JsonProperty property = type.getField(constant.name()).getAnnotation(JsonProperty.class);
            return property == null ? constant.name() : property.value();
        } catch (NoSuchFieldException e) {
            return constant.name();
        }
    }
}
