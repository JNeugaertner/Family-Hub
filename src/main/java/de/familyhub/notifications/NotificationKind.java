package de.familyhub.notifications;

// Arten von Benachrichtigungen (Entscheidungen vom 21.10.2026). Jede Person kann jede Art für sich abschalten.
public enum NotificationKind {
    // Dringende Aufgaben: zugewiesen oder auf "hoch" gestellt, und morgens am Fälligkeitstag (TaskNotifications)
    TASKS,
    // Kurzfristige Termine: neu oder verschoben, Beginn in den nächsten 24 Stunden (EventNotifications)
    EVENTS,
    // Bestätigungen, die seit 24 Stunden offen sind, an alle, die bestätigen dürfen (PendingConfirmations)
    CONFIRMATIONS,
    // Chat-Nachrichten in FamilyHub (MessageNotifications)
    MESSAGES
}
