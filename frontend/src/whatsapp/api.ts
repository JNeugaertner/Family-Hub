import { request } from '../api/client';

// Status der WhatsApp-Anbindung. "configured" ist ein Serverzustand (Zugangsdaten in
// local.properties / Umgebungsvariablen vorhanden) – es gibt keine pro-Mitglied-Anmeldung,
// da die Meta-Cloud-API serverseitig verbunden wird (siehe README).
export interface WhatsAppStatus {
  configured: boolean;
  // Anzahl der bisher empfangenen und roh gespeicherten WhatsApp-Nachrichten
  messageCount: number;
}

export const getWhatsAppStatus = () => request<WhatsAppStatus>('/api/whatsapp/status');
