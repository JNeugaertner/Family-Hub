package de.familyhub.messages;

import java.util.Optional;

// Kennungen der Unterhaltungen: die Familiengruppe "family" und Einzelchats "direct:<id>:<id>" mit der kleineren
// Mitglieds-ID zuerst, damit beide Personen denselben Chat finden.
public final class Conversations {

    public static final String FAMILY = "family";

    private static final String DIRECT = "direct:";

    private Conversations() {
    }

    public static String direct(String memberId, String otherId) {
        return memberId.compareTo(otherId) < 0 ? DIRECT + memberId + ":" + otherId : DIRECT + otherId + ":" + memberId;
    }

    public static boolean isFamily(String conversation) {
        return FAMILY.equals(conversation);
    }

    // Die andere Person eines Einzelchats, wenn memberId daran beteiligt ist; sonst leer (auch bei ungültiger Kennung)
    public static Optional<String> partnerOf(String conversation, String memberId) {
        if (conversation == null || !conversation.startsWith(DIRECT)) {
            return Optional.empty();
        }
        String[] ids = conversation.substring(DIRECT.length()).split(":", -1);
        if (ids.length != 2 || ids[0].isEmpty() || ids[1].isEmpty() || !direct(ids[0], ids[1]).equals(conversation)
                || ids[0].equals(ids[1])) {
            return Optional.empty();
        }
        if (ids[0].equals(memberId)) {
            return Optional.of(ids[1]);
        }
        return ids[1].equals(memberId) ? Optional.of(ids[0]) : Optional.empty();
    }
}
