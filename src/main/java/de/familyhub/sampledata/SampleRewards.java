package de.familyhub.sampledata;

import static de.familyhub.rewards.RewardCategory.AUSFLUG;
import static de.familyhub.rewards.RewardCategory.ESSEN;
import static de.familyhub.rewards.RewardCategory.FREIZEIT;
import static de.familyhub.rewards.RewardCategory.GESCHENK;

import java.util.List;

import de.familyhub.rewards.Reward;

// Belohnungen aus der früheren Vorschau im Figma-UI (Rewards.tsx). Einmalig sind die größeren Belohnungen.
final class SampleRewards {

    static final List<Reward> REWARDS = List.of(
            new Reward(null, "🍽️", "Lieblingsessen", "Du bestimmst, was heute Abend gekocht wird.", 50, ESSEN, true, true),
            new Reward(null, "📱", "1 Std. Extra-Bildschirmzeit", "Eine Stunde zusätzliche Bildschirmzeit an einem Tag.",
                    100, FREIZEIT, true, true),
            new Reward(null, "🎬", "Film-Abend Wahl", "Du wählst den Familienfilm am Freitagabend.", 150, FREIZEIT, true,
                    true),
            new Reward(null, "🌙", "30 Min. länger wach", "Einmal darfst du 30 Minuten später ins Bett.", 150, FREIZEIT,
                    true, true),
            new Reward(null, "🎮", "2 Std. Gaming", "Zwei Stunden ununterbrochenes Spielen am Wochenende.", 200, FREIZEIT,
                    true, true),
            new Reward(null, "🍕", "Pizza bestellen", "Wir bestellen Pizza – du wählst den Belag!", 200, ESSEN, true, true),
            new Reward(null, "🏊", "Schwimmbad-Tag", "Ein ganzer Tag im Schwimmbad mit der Familie.", 250, AUSFLUG, true,
                    true),
            new Reward(null, "🎪", "Freizeitpark-Besuch", "Ein Ausflug in einen Freizeitpark deiner Wahl.", 300, AUSFLUG,
                    true, false),
            new Reward(null, "🎁", "Neues Buch oder Spiel", "Ein neues Buch oder kleines Spiel deiner Wahl.", 400,
                    GESCHENK, true, false),
            new Reward(null, "🛍️", "Shopping-Gutschein 10€", "10€ für deinen Lieblings-Onlineshop.", 500, GESCHENK,
                    false, false));

    private SampleRewards() {
    }
}
