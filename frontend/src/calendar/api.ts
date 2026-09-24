import type { EventCategory } from '../components/data';

const BASE_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export interface ApiMember {
  id: string;
  name: string;
  color: string;
}

export interface ApiEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  memberId: string;
  category: EventCategory;
  location: string | null;
  description: string | null;
}

export type EventInput = Omit<ApiEvent, 'id' | 'start' | 'end'> & {
  start: string | null;
  end: string | null;
};

// Fehlerformat des Backends (RFC 9457), Feldfehler unter "errors".
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = init.body ? { 'Content-Type': 'application/json' } : undefined;
  let response: Response;
  try {
    response = await fetch(BASE_URL + path, { ...init, headers });
  } catch {
    throw new ApiError(0, {
      title: 'Backend nicht erreichbar',
      detail: `Keine Verbindung zu ${BASE_URL}. Läuft das Backend (mvnw spring-boot:run) und MongoDB?`,
    });
  }
  if (!response.ok) {
    const problem: ProblemDetail = await response.json().catch(() => ({}));
    throw new ApiError(response.status, problem);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export const listMembers = () => request<ApiMember[]>('/api/members');

export const listEvents = () => request<ApiEvent[]>('/api/events');

export const createEvent = (input: EventInput) =>
  request<ApiEvent>('/api/events', { method: 'POST', body: JSON.stringify(input) });

export const updateEvent = (id: string, input: EventInput) =>
  request<ApiEvent>(`/api/events/${id}`, { method: 'PUT', body: JSON.stringify(input) });

export const deleteEvent = (id: string) =>
  request<void>(`/api/events/${id}`, { method: 'DELETE' });
