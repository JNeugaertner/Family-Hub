import { useCallback, useEffect, useState } from 'react';
import { useAuth, useMe } from '../auth/AuthContext';
import { listAchievementProgress } from '../achievements/api';
import { listHistory } from './api';
import PointsToast from './PointsToast';

// Browser-Merker: bis wohin die Person ihre Punkte und Erfolge schon gesehen hat. Fehlt der Speicher
// (privates Fenster, gesperrt), gibt es einfach keinen Hinweis.
function readSeen(key: string): string | null {
  try { return window.localStorage.getItem(key); } catch { return null; }
}

function writeSeen(key: string, value: string) {
  try { window.localStorage.setItem(key, value); } catch { /* ohne Speicher kein Hinweis */ }
}

interface Notice { icon: string; text: string; }

// Kinder und Jugendliche sehen nach dem Anmelden, welche Punkte und Erfolge seit ihrem letzten Besuch
// dazugekommen sind, nacheinander als kurze Animation.
export default function NewPointsNotice() {
  const me = useMe();
  const { can } = useAuth();
  const active = can('punkte', 'ansehen', 'eigen') && !can('punkte', 'freigeben', 'familie');
  const [queue, setQueue] = useState<Notice[]>([]);
  const next = useCallback(() => setQueue(q => q.slice(1)), []);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    const show = (notices: Notice[]) => { if (!cancelled && notices.length) setQueue(q => [...q, ...notices]); };

    // Merker erst schreiben, wenn der Durchlauf nicht abgebrochen wurde (React führt Effekte in der
    // Entwicklung doppelt aus; sonst verbraucht der abgebrochene Lauf die Neuigkeiten).
    listHistory(me.id).then(entries => {
      if (cancelled || entries.length === 0) return;
      const key = `familyhub.pointsSeen.${me.id}`;
      const seen = readSeen(key);
      writeSeen(key, entries[0].createdAt);
      if (!seen) return; // erster Besuch: die bisherige Historie nicht feiern
      // Nur Punkte für erledigte Aufgaben; Rückbuchungen und Erfolgs-Boni haben eigene bzw. keine Meldung
      const fresh = entries.filter(e => e.createdAt > seen && e.amount > 0 && e.taskId);
      if (fresh.length === 0) return;
      const total = fresh.reduce((sum, e) => sum + e.amount, 0);
      show([{ icon: '⭐', text: fresh.length === 1
        ? `Neue Punkte: +${total} · ${fresh[0].reason}`
        : `Neue Punkte: +${total} für ${fresh.length} erledigte Aufgaben` }]);
    }).catch(() => { /* Hinweis ist optional */ });

    listAchievementProgress().then(all => {
      if (cancelled) return;
      const earned = (all.find(m => m.memberId === me.id)?.items ?? [])
        .filter(i => i.earnedAt)
        .sort((a, b) => a.earnedAt!.localeCompare(b.earnedAt!));
      const key = `familyhub.achievementsSeen.${me.id}`;
      const seen = readSeen(key);
      // Auch ohne Erfolge einen Merker setzen ("0" liegt vor jedem Datum), damit der erste Erfolg gefeiert wird
      writeSeen(key, earned.length ? earned[earned.length - 1].earnedAt! : (seen ?? '0'));
      if (!seen) return; // erster Besuch: bisherige Erfolge nicht feiern
      show(earned.filter(i => i.earnedAt! > seen).map(i => ({
        icon: '🏆',
        text: `Neuer Erfolg: ${i.icon} ${i.name}${i.bonus > 0 ? ` · +${i.bonus} Punkte` : ''}`,
      })));
    }).catch(() => { /* Hinweis ist optional */ });

    return () => { cancelled = true; };
  }, [active, me.id]);

  const current = queue[0];
  return current ? <PointsToast key={current.text} icon={current.icon} text={current.text} onDone={next} duration={5000} /> : null;
}
