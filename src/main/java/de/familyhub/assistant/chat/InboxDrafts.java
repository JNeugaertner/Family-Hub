package de.familyhub.assistant.chat;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantAccess;
import de.familyhub.assistant.AssistantProperties;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.assistant.settings.AssistantSettings;
import de.familyhub.assistant.settings.AssistantSettingsService;
import de.familyhub.family.FamilyMember;
import de.familyhub.inbox.Inbox;
import de.familyhub.inbox.InboundMessage;
import de.familyhub.inbox.InboundMessageReceived;
import de.familyhub.live.LiveUpdates;

// Automatische Auswertung des Eingangs (Phase 5, Entscheidungen vom 22.10.2026): Schreibt ein Familienmitglied
// FamilyHub per WhatsApp oder Telegram, bereitet der Assistent Entwürfe dafür in dessen Chat vor. Nur, wenn ein
// Administrator das eingeschaltet hat (standardmäßig aus), nur für Absender, die einem Familienmitglied zugeordnet
// sind (Nachrichten Unbekannter gehen nie an Groq), und ohne Benachrichtigung. Läuft auf einem eigenen Thread, damit
// WhatsApp und Telegram nicht auf Groq warten; in Tests sofort (familyhub.assistant.async=false).
@Component
public class InboxDrafts implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(InboxDrafts.class);

    private final AssistantChat chat;
    private final AssistantSettingsService settings;
    private final AssistantProperties properties;
    private final AssistantAccess access;
    private final Inbox inbox;
    private final LiveUpdates live;
    // null: sofort auswerten
    private final ExecutorService worker;

    public InboxDrafts(AssistantChat chat, AssistantSettingsService settings, AssistantProperties properties,
            AssistantAccess access, Inbox inbox, LiveUpdates live,
            @Value("${familyhub.assistant.async:true}") boolean async) {
        this.chat = chat;
        this.settings = settings;
        this.properties = properties;
        this.access = access;
        this.inbox = inbox;
        this.live = live;
        // Ein Thread genügt: Nachrichten kommen selten, und Groq begrenzt ohnehin die Anfragen pro Minute
        this.worker = async ? Executors.newSingleThreadExecutor(Thread.ofVirtual().name("assistant-inbox").factory())
                : null;
    }

    @EventListener
    public void received(InboundMessageReceived event) {
        InboundMessage message = event.message();
        AssistantSettings current = settings.current();
        if (!properties.isConfigured() || !current.enabled() || !current.inboxDrafts()
                || message.text() == null || message.text().isBlank()) {
            return;
        }
        Optional<FamilyMember> sender = inbox.senderOf(message).filter(access::mayUse);
        if (sender.isEmpty()) {
            LOG.debug("Nachricht {} nicht ausgewertet: Absender ist kein Familienmitglied mit KI-Assistent",
                    message.id());
            return;
        }
        if (worker == null) {
            evaluate(sender.get(), message);
        } else {
            worker.execute(() -> evaluate(sender.get(), message));
        }
    }

    private void evaluate(FamilyMember member, InboundMessage message) {
        try {
            if (!chat.fromInbox(member, message.channel().displayName(), message.text().strip(),
                    message.receivedAt())) {
                LOG.info("Nachricht {} nicht ausgewertet: Tageslimit von Mitglied {} erreicht", message.id(),
                        member.id());
                return;
            }
            // Der Chat der Person soll die Entwürfe ohne Neuladen zeigen
            live.publish("assistant");
        } catch (LanguageModelException e) {
            LOG.warn("Nachricht {} nicht ausgewertet: {}", message.id(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Nachricht {} nicht ausgewertet", message.id(), e);
        }
    }

    @Override
    public void destroy() {
        if (worker != null) {
            worker.shutdown();
        }
    }
}
