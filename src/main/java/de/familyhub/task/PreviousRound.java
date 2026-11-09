package de.familyhub.task;

import java.time.LocalDate;

// Bei wiederkehrenden Aufgaben: die Runde davor, wer sie wann abgehakt hat. Steht in der neuen Runde, damit
// "zuletzt erledigt" auch noch stimmt, wenn die alte Runde gelöscht ist.
public record PreviousRound(String taskId, LocalDate doneOn, String doneBy) {
}
