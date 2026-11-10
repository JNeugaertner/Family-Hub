import { json, request } from '../api/client';
import type { EventCategory, TransportMode } from '../components/data';

// Vorlage für Termine mit gleichem Ort und gleicher Uhrzeit (z. B. Zahnarzt), siehe EventTemplate im Backend.
// Sie füllt nur das Termin-Formular aus; gespeichert und geprüft wird der Termin wie jeder andere.
export interface EventTemplate {
  id: string;
  name: string;
  title: string;
  // "HH:mm"
  startTime: string;
  durationMinutes: number;
  memberIds: string[];
  category: EventCategory;
  location: string | null;
  lat: number | null;
  lon: number | null;
  transportMode: TransportMode | null;
  travelTime: number | null;
  description: string | null;
  private: boolean;
  createdBy: string | null;
}

export type EventTemplateInput = Omit<EventTemplate, 'id' | 'createdBy'>;

export const listTemplates = () => request<EventTemplate[]>('/api/event-templates');

export const createTemplate = (input: EventTemplateInput) =>
  request<EventTemplate>('/api/event-templates', { method: 'POST', body: json(input) });

export const updateTemplate = (id: string, input: EventTemplateInput) =>
  request<EventTemplate>(`/api/event-templates/${id}`, { method: 'PUT', body: json(input) });

export const deleteTemplate = (id: string) => request<void>(`/api/event-templates/${id}`, { method: 'DELETE' });

export const inputOf = ({ id: _id, createdBy: _createdBy, ...input }: EventTemplate): EventTemplateInput => input;

const pad = (n: number) => String(n).padStart(2, '0');

// "JJJJ-MM-TTTHH:mm" in Ortszeit plus Minuten (über Mitternacht hinaus und mit Zeitumstellung richtig)
export function addMinutes(dateTime: string, minutes: number): string {
  const d = new Date(dateTime);
  d.setMinutes(d.getMinutes() + minutes);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// Minuten zwischen Beginn und Ende ("JJJJ-MM-TTTHH:mm"), gerundet
export const minutesBetween = (start: string, end: string) =>
  Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000);

// Beginn und Ende eines Termins am Tag date ("JJJJ-MM-TT") nach der Vorlage
export function timesFor(template: EventTemplate, date: string) {
  const start = `${date}T${template.startTime}`;
  return { start, end: addMinutes(start, template.durationMinutes) };
}

// "08:30, 45 Min." bzw. "08:30, 1 Std. 30 Min."
export function describeTime(template: EventTemplate) {
  const h = Math.floor(template.durationMinutes / 60);
  const m = template.durationMinutes % 60;
  const duration = [h ? `${h} Std.` : '', m ? `${m} Min.` : ''].filter(Boolean).join(' ');
  return `${template.startTime}, ${duration}`;
}
