package de.familyhub.calendar;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CalendarProperties.class)
public class CalendarConfig {
}
