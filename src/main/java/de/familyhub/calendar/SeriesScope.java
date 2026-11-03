package de.familyhub.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;

// Was eine Änderung oder ein Löschen an einem Serientermin betrifft: nur diesen Termin oder diesen und alle
// folgenden (Entscheidung vom 03.11.2026; "ganze Serie samt Vergangenheit" gibt es bewusst nicht).
public enum SeriesScope {
    @JsonProperty("single") SINGLE,
    @JsonProperty("following") FOLLOWING
}
