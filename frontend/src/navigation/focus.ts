import { createContext, useContext, useEffect } from 'react';

// Sprungziel beim Seitenwechsel, z. B. aus der Übersicht: ein bestimmter Termin (an seinem Tag), ein Tag im Kalender
// oder eine Aufgabe.
// Die Zielseite scrollt zum Element mit data-focus-id und lässt es kurz aufleuchten (Klasse focus-flash, index.css).
export type Focus =
  | { kind: 'event'; id: string; date: string }
  | { kind: 'day'; date: string }
  | { kind: 'task'; id: string };

interface FocusState {
  focus: Focus | null;
  clear: () => void;
}

export const FocusContext = createContext<FocusState>({ focus: null, clear: () => {} });

export const useFocus = () => useContext(FocusContext);

// ready: die Seite hat ihre Daten geladen und die passende Ansicht gezeigt
// Nur Termine und Aufgaben leuchten auf; ein Tag hat kein einzelnes Element dafür
export function useFlashFocus(kind: 'event' | 'task', ready: boolean) {
  const { focus, clear } = useFocus();
  useEffect(() => {
    if (!ready || !focus || focus.kind !== kind) return;
    let tries = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    // Das Element erscheint evtl. erst nach dem nächsten Rendern (z. B. nach dem Wechsel in die Tagesansicht)
    const find = () => {
      const el = document.querySelector<HTMLElement>(`[data-focus-id="${CSS.escape(focus.id)}"]`);
      if (!el) {
        if (tries++ < 20) timer = setTimeout(find, 50);
        else clear();
        return;
      }
      el.scrollIntoView({ block: 'center', behavior: 'smooth' });
      el.classList.remove('focus-flash');
      void el.offsetWidth; // Animation neu starten, falls sie schon lief
      el.classList.add('focus-flash');
      el.addEventListener('animationend', () => el.classList.remove('focus-flash'), { once: true });
      clear();
    };
    find();
    return () => clearTimeout(timer);
  }, [focus, kind, ready, clear]);
}
