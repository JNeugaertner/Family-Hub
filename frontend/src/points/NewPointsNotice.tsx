import { useCallback, useEffect, useState } from 'react';
import { useAuth, useMe } from '../auth/AuthContext';
import { listHistory } from './api';
import PointsToast from './PointsToast';

// Browser-Merker: bis zu welcher Buchung die Person ihre Punkte schon gesehen hat. Fehlt der Speicher
// (privates Fenster, gesperrt), gibt es einfach keinen Hinweis.
function readSeen(key: string): string | null {
  try { return window.localStorage.getItem(key); } catch { return null; }
}

function writeSeen(key: string, value: string) {
  try { window.localStorage.setItem(key, value); } catch { /* ohne Speicher kein Hinweis */ }
}

// Kinder und Jugendliche sehen nach dem Anmelden, welche Punkte seit ihrem letzten Besuch dazugekommen sind.
export default function NewPointsNotice() {
  const me = useMe();
  const { can } = useAuth();
  const active = can('punkte', 'ansehen', 'eigen') && !can('punkte', 'freigeben', 'familie');
  const [text, setText] = useState<string | null>(null);
  const hide = useCallback(() => setText(null), []);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    listHistory(me.id).then(entries => {
      if (cancelled || entries.length === 0) return;
      const key = `familyhub.pointsSeen.${me.id}`;
      const seen = readSeen(key);
      writeSeen(key, entries[0].createdAt);
      if (!seen) return; // erster Besuch: die bisherige Historie nicht feiern
      const fresh = entries.filter(e => e.createdAt > seen && e.amount > 0);
      if (fresh.length === 0) return;
      const total = fresh.reduce((sum, e) => sum + e.amount, 0);
      setText(fresh.length === 1
        ? `Neue Punkte: +${total} · ${fresh[0].reason}`
        : `Neue Punkte: +${total} für ${fresh.length} erledigte Aufgaben`);
    }).catch(() => { /* Hinweis ist optional */ });
    return () => { cancelled = true; };
  }, [active, me.id]);

  return text ? <PointsToast text={text} onDone={hide} duration={5000} /> : null;
}
