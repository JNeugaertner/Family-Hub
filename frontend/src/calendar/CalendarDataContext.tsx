import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { CalendarEvent } from '../components/data';
import * as api from './api';

export interface CalendarMember {
  id: string;
  name: string;
  color: string;
  initials: string;
}

type Status = 'loading' | 'ready' | 'error';

interface CalendarData {
  status: Status;
  error: string | null;
  members: CalendarMember[];
  events: CalendarEvent[];
  memberById: (id: string) => CalendarMember | undefined;
  reload: () => Promise<void>;
  saveEvent: (input: api.EventInput, id?: string) => Promise<void>;
  removeEvent: (id: string) => Promise<void>;
}

const CalendarDataContext = createContext<CalendarData | null>(null);

function toMember(m: api.ApiMember): CalendarMember {
  return { ...m, initials: m.name.slice(0, 2).toUpperCase() };
}

// Backend liefert "2026-09-25T10:00:00"; die Ansichten gruppieren nach Datum und HH:mm.
function toEvent(e: api.ApiEvent): CalendarEvent {
  return {
    id: e.id,
    title: e.title,
    start: e.start,
    end: e.end,
    date: e.start.slice(0, 10),
    time: e.start.slice(11, 16),
    endTime: e.end.slice(11, 16),
    memberId: e.memberId,
    category: e.category,
    location: e.location ?? undefined,
    description: e.description ?? undefined,
  };
}

export function CalendarDataProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [error, setError] = useState<string | null>(null);
  const [members, setMembers] = useState<CalendarMember[]>([]);
  const [events, setEvents] = useState<CalendarEvent[]>([]);

  const reload = useCallback(async () => {
    try {
      const [m, e] = await Promise.all([api.listMembers(), api.listEvents()]);
      setMembers(m.map(toMember));
      setEvents(e.map(toEvent));
      setError(null);
      setStatus('ready');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setStatus('error');
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const saveEvent = useCallback(async (input: api.EventInput, id?: string) => {
    if (id) await api.updateEvent(id, input);
    else await api.createEvent(input);
    await reload();
  }, [reload]);

  const removeEvent = useCallback(async (id: string) => {
    await api.deleteEvent(id);
    await reload();
  }, [reload]);

  const value = useMemo<CalendarData>(() => ({
    status, error, members, events,
    memberById: (id: string) => members.find(m => m.id === id),
    reload, saveEvent, removeEvent,
  }), [status, error, members, events, reload, saveEvent, removeEvent]);

  return <CalendarDataContext.Provider value={value}>{children}</CalendarDataContext.Provider>;
}

export function useCalendarData(): CalendarData {
  const ctx = useContext(CalendarDataContext);
  if (!ctx) throw new Error('useCalendarData muss innerhalb von CalendarDataProvider verwendet werden');
  return ctx;
}
