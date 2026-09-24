// Gemeinsame Verbindung zum Backend. Im Dev-Server leitet Vite /api an http://localhost:8080 weiter
// (vite.config.ts), daher reicht standardmäßig ein relativer Pfad. VITE_API_URL überschreibt das.
const BASE_URL: string = import.meta.env.VITE_API_URL ?? '';

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

// Wird aufgerufen, wenn das Backend "nicht angemeldet" meldet (z. B. Sitzung abgelaufen).
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

// Das Backend legt das CSRF-Token im Cookie XSRF-TOKEN ab; jede Änderung muss es als Header mitschicken.
function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find(c => c.startsWith('XSRF-TOKEN='))
    ?.substring('XSRF-TOKEN='.length);
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json');
  const token = csrfToken();
  if (method !== 'GET' && token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token));

  let response: Response;
  try {
    response = await fetch(BASE_URL + path, { ...init, method, headers, credentials: 'include' });
  } catch {
    throw new ApiError(0, {
      title: 'Backend nicht erreichbar',
      detail: 'Keine Verbindung zum Backend. Läuft es (mvnw spring-boot:run) und läuft MongoDB?',
    });
  }
  if (!response.ok) {
    const problem: ProblemDetail = await response.json().catch(() => ({}));
    if (response.status === 401 && !path.startsWith('/api/auth/')) onUnauthorized();
    throw new ApiError(response.status, problem);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export const json = (body: unknown) => JSON.stringify(body);
