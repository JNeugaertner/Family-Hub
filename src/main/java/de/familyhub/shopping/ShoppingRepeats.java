package de.familyhub.shopping;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.familyhub.live.LiveUpdates;

// Wiederkehrende Artikel mit Abstand kommen am Tag "Kauf + Abstand" offen zurück auf die Liste, auch wenn sie noch
// abgehakt dastehen. Läuft kurz nach Mitternacht und beim Start (falls der Server um Mitternacht aus war); offene
// Browser laden die Liste dann neu.
@Component
public class ShoppingRepeats {

    private final ShoppingItemRepository items;
    private final LiveUpdates live;
    private final Clock clock;

    public ShoppingRepeats(ShoppingItemRepository items, LiveUpdates live, Clock clock) {
        this.items = items;
        this.live = live;
        this.clock = clock;
    }

    @Scheduled(cron = "${familyhub.shopping.return-cron:0 1 0 * * *}")
    @EventListener(ApplicationReadyEvent.class)
    public void returnDueItems() {
        LocalDate today = LocalDate.now(clock);
        List<ShoppingItem> due = items.findByCheckedTrueAndRepeatNotNull().stream()
                .filter(item -> item.isDueBack(today))
                .map(ShoppingItem::reopened)
                .toList();
        if (!due.isEmpty()) {
            items.saveAll(due);
            live.publish("shopping");
        }
    }
}
