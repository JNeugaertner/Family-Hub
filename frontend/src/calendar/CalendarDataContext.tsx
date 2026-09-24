import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { CalendarEvent } from '../components/data';
import { listMembers, type ApiMember } from '../family/api';
import type { RoleId } from '../roles';
import * as api from './api';

export interface CalendarMember {
  id: string;
  name: string;
  color: string;
  initials: string;
  effectiveRole: RoleId;
}

type Status = 'loading' | 'ready' | 'error';

interface CalendarData {
  status: Status;
  error: string | null;
  members: CalendarMember[];
  // Nur die Termine, die die angemeldete Person sehen darf (filtert das Backend)
  events: CalendarEvent[];
  memberById: (id: string) => CalendarMember | undefined;
  reload: () => Promise<void>;
  saveEvent: (input: api.EventInput, id?: string) => Promise<void>;
  removeEvent: (id: string) => Promise<void>;
  approveEvent: (id: string) => Promise<void>;
  rejectEvent: (id: string) => Promise<void>;
}

const CalendarDataContext = createContext<CalendarData | null>(null);

function toMember(m: ApiMember): CalendarMember {
  return { id: m.id, name: m.name, color: m.color, initials: m.name.slice(0, 2).toUpperCase(), effectiveRole: m.effectiveRole };
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
    private: e.private,
    status: e.status,
    createdBy: e.createdBy ?? undefined,
  };
}

export function CalendarDataProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [error, setError] = useState<string | null>(null);
  const [members, setMembers] = useState<CalendarMember[]>([]);
  const [events, setEvents] = useState<CalendarEvent[]>([]);

  const reload = useCallback(async () => {
    try {
      const [m, e] = await Promise.all([listMembers(), api.listEvents()]);
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

  const afterChange = useCallback(<A extends unknown[]>(action: (...args: A) => Promise<unknown>) =>
    async (...args: A) => {
      await action(...args);
      await reload();
    }, [reload]);

  const value = useMemo<CalendarData>(() => ({
    status, error, members, events,
    memberById: (id: string) => members.find(m => m.id === id),
    reload,
    saveEvent: afterChange((input: api.EventInput, id?: string) => id ? api.updateEvent(id, input) : api.createEvent(input)),
    removeEvent: afterChange(api.deleteEvent),
    approveEvent: afterChange(api.approveEvent),
    rejectEvent: afterChange(api.rejectEvent),
  }), [status, error, members, events, reload, afterChange]);

  return <CalendarDataContext.Provider value={value}>{children}</CalendarDataContext.Provider>;
}

export function useCalendarData(): CalendarData {
  const ctx = useContext(CalendarDataContext);
  if (!ctx) throw new Error('useCalendarData muss innerhalb von CalendarDataProvider verwendet werden');
  return ctx;
}
