package de.familyhub.calendar;

import java.time.Period;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Einstellungen für den Kalender. Wann die Serien täglich verlängert werden, steht an EventSeriesService.extendAll
// (familyhub.calendar.series-extend-cron).
@ConfigurationProperties("familyhub.calendar")
public record CalendarProperties(
        // So weit im Voraus legt FamilyHub die einzelnen Termine einer Serie an
        @DefaultValue("P12M") Period seriesHorizon) {
}
