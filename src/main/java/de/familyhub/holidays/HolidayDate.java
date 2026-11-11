package de.familyhub.holidays;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ein Feiertag an einem Datum, z. B. {\"key\": \"weihnachten\", \"name\": \"Weihnachten\", "
        + "\"emoji\": \"🎄\", \"date\": \"2026-12-24\", \"daysLeft\": 43}")
public record HolidayDate(
        @Schema(description = "Feste Kennung: neujahr, ostern, christi_himmelfahrt, pfingsten, "
                + "tag_der_deutschen_einheit oder weihnachten")
        String key,
        String name,
        String emoji,
        LocalDate date,
        @Schema(description = "Tage ab heute; 0 heißt heute, negativ liegt der Tag zurück")
        long daysLeft) {
}
