package de.familyhub.inbox;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

// Kanäle, über die Nachrichten von außen in FamilyHub ankommen.
public enum Channel {
    @JsonProperty("whatsapp") WHATSAPP("WhatsApp"),
    @JsonProperty("telegram") TELEGRAM("Telegram");

    private final String displayName;

    Channel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    // Kennung, die über alle Kanäle eindeutig ist, z. B. "whatsapp:4917012345678" für einen Absender oder
    // "telegram:-100:42" für eine Nachricht
    public String prefixed(String value) {
        return name().toLowerCase(Locale.ROOT) + ":" + value;
    }
}
