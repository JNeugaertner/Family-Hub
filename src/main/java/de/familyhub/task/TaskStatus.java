package de.familyhub.task;

import com.fasterxml.jackson.annotation.JsonProperty;

// offen -> in Arbeit -> erledigt -> bestätigt. Erledigte Aufgaben mit Punkten warten auf die Bestätigung
// durch einen Administrator; erst dann werden die Punkte gutgeschrieben und die Aufgabe ist abgeschlossen.
public enum TaskStatus {
    @JsonProperty("todo") TODO,
    @JsonProperty("inprogress") IN_PROGRESS,
    @JsonProperty("done") DONE,
    @JsonProperty("confirmed") CONFIRMED
}
