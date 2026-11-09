package de.familyhub.notifications;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMember;
import de.familyhub.telegram.TelegramService;

// Schickt Benachrichtigungen an Familienmitglieder (Entscheidung vom 21.10.2026): über Telegram an alle mit
// verknüpftem Konto, außer sie haben die Art abgeschaltet. Was eine Benachrichtigung auslöst, steht in
// TaskNotifications, EventNotifications, PendingConfirmations und MessageNotifications.
// Gesendet wird nacheinander im Hintergrund: Ist Telegram nicht erreichbar, steht das nur im Log, und die Anfrage,
// die die Benachrichtigung ausgelöst hat, wartet nie darauf.
@Service
public class Notifications implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(Notifications.class);

    private final TelegramService telegram;
    private final NotificationSettingsRepository settings;
    // null: sofort senden (familyhub.notifications.async=false, in Tests)
    private final ExecutorService sender;

    public Notifications(TelegramService telegram, NotificationSettingsRepository settings,
            NotificationProperties properties) {
        this.telegram = telegram;
        this.settings = settings;
        this.sender = properties.async()
                ? Executors.newSingleThreadExecutor(Thread.ofVirtual().name("notifications").factory())
                : null;
    }

    public void send(FamilyMember recipient, NotificationKind kind, String text) {
        if (!telegram.isConfigured() || !enabledFor(recipient).contains(kind)) {
            return;
        }
        if (sender == null) {
            deliver(recipient, text);
        } else {
            sender.execute(() -> deliver(recipient, text));
        }
    }

    public Set<NotificationKind> enabledFor(FamilyMember member) {
        EnumSet<NotificationKind> enabled = EnumSet.allOf(NotificationKind.class);
        settings.findById(member.id()).ifPresent(s -> enabled.removeAll(s.muted()));
        return enabled;
    }

    public void setEnabled(FamilyMember member, Set<NotificationKind> enabled) {
        EnumSet<NotificationKind> muted = EnumSet.allOf(NotificationKind.class);
        muted.removeAll(enabled);
        settings.save(new NotificationSettings(member.id(), muted));
    }

    // Beim Löschen eines Familienmitglieds
    public void forgetMember(String memberId) {
        settings.deleteById(memberId);
    }

    @Override
    public void destroy() {
        if (sender != null) {
            sender.shutdown();
        }
    }

    private void deliver(FamilyMember recipient, String text) {
        try {
            telegram.sendTo(recipient, text);
        } catch (RuntimeException e) {
            LOG.warn("Benachrichtigung an Mitglied {} nicht gesendet: {}", recipient.id(), e.getMessage());
        }
    }
}
