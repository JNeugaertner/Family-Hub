package de.familyhub.telegram;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

// Holt neue Nachrichten an den Bot bei Telegram ab (Long Polling): Eine Anfrage bleibt bis zu 25 Sekunden offen und
// kommt zurück, sobald jemand schreibt. FamilyHub braucht dafür keine öffentliche Adresse, nur ausgehende
// Verbindungen zu api.telegram.org (Entscheidung vom 21.10.2026). Läuft in einem eigenen Thread, damit die lange
// offene Anfrage die geplanten Aufgaben (Google-Abgleich, Müll-Aufgaben) nicht aufhält.
@Component
class TelegramPoller implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramPoller.class);

    // Wartezeit nach einem Fehler, damit FamilyHub Telegram nicht im Sekundentakt anfragt
    static final Duration RETRY_DELAY = Duration.ofSeconds(15);

    private final TelegramApi api;
    private final TelegramService service;
    private final TelegramProperties properties;

    private volatile boolean running;
    private Thread thread;
    // Nächste erwartete Update-Id; bestätigt Telegram zugleich alle älteren. Nach einem Neustart liefert Telegram
    // noch nicht bestätigte Nachrichten erneut, der Eingang erkennt sie wieder.
    private Long offset;

    TelegramPoller(TelegramApi api, TelegramService service, TelegramProperties properties) {
        this.api = api;
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.polling() || !properties.isConfigured()) {
            return;
        }
        running = true;
        thread = Thread.ofVirtual().name("telegram-poller").start(this::loop);
        LOG.info("Telegram: Nachrichten an den Bot werden abgeholt");
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                pollOnce(TelegramApi.POLL_SECONDS);
            } catch (RuntimeException e) {
                LOG.warn("Telegram-Abruf fehlgeschlagen: {}", e.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    // Eine Abfrage; jede Nachricht einzeln, damit eine fehlerhafte die übrigen nicht blockiert
    synchronized void pollOnce(int timeoutSeconds) {
        for (JsonNode update : api.getUpdates(offset, timeoutSeconds)) {
            offset = update.path("update_id").asLong() + 1;
            try {
                service.handle(update);
            } catch (RuntimeException e) {
                LOG.warn("Telegram-Nachricht {} konnte nicht verarbeitet werden", update.path("update_id").asLong(),
                        e);
            }
        }
    }
}
