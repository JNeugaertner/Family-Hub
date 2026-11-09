package de.familyhub.whatsapp;

// Ein eingehender WhatsApp-Webhook ist fehlbar (z. B. kein gültiges JSON); die Meldung ist fürs Log gedacht.
public class WhatsAppException extends RuntimeException {

    public WhatsAppException(String message) {
        super(message);
    }
}
