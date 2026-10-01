// Live-Verbindung zum Backend (Server-Sent Events, GET /api/live): meldet nach jeder Änderung den Bereich,
// z. B. "tasks" oder "shopping", ohne Inhalte. Eine Verbindung für die ganze Seite, egal wie viele Datenbereiche
// zuhören; sie schließt, wenn niemand mehr zuhört (z. B. nach dem Abmelden).

type Listener = (areas: string[]) => void;

// Nach einem Verbindungsabbruch können Meldungen fehlen: dann alles neu laden
export const EVERYTHING = '*';

const listeners = new Set<Listener>();
let source: EventSource | null = null;
let interrupted = false;

const notify = (areas: string[]) => listeners.forEach(listener => listener(areas));

function connect() {
  source = new EventSource('/api/live');
  source.addEventListener('aenderung', event => notify([(event as MessageEvent<string>).data]));
  // Der Browser verbindet sich nach einem Abbruch von selbst neu
  source.onerror = () => { interrupted = true; };
  source.onopen = () => {
    if (interrupted) notify([EVERYTHING]);
    interrupted = false;
  };
}

export function subscribeLive(listener: Listener): () => void {
  listeners.add(listener);
  if (!source) connect();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      source?.close();
      source = null;
      interrupted = false;
    }
  };
}
