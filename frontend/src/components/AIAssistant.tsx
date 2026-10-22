import { useCallback, useEffect, useRef, useState } from 'react';
import { SendIcon, SparklesIcon } from './Icons';
import { useAuth, useMe } from '../auth/AuthContext';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { askAssistant, clearConversation, getAssistantStatus, getConversation, type AssistantStatus, type ChatTurn, type Draft } from '../assistant/api';
import DraftCard from '../assistant/DraftCard';

interface Props { onNavigate: (p: any) => void; }

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

// Einstiegsfragen für ein leeres Gespräch
const SUGGESTIONS = [
  'Was steht heute an?',
  'Welche Aufgaben sind noch offen?',
  'Was fehlt auf der Einkaufsliste?',
  'Was gibt es diese Woche zu essen?',
  'Wie wird das Wetter morgen?',
  'Setz Milch auf die Einkaufsliste',
  'Was gibt es Neues im Familienchat?',
];

const timeOf = (iso: string) => new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });

function TypingIndicator() {
  return (
    <div className="flex items-start gap-3" role="status" aria-label="Der Assistent denkt nach">
      <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center flex-shrink-0">
        <SparklesIcon size={14} className="text-white" />
      </div>
      <div className="bg-white border border-slate-100 rounded-2xl rounded-tl-sm px-4 py-3">
        <div className="flex items-center gap-1">
          {[0, 1, 2].map(i => (
            <div key={i} className="typing-dot w-2 h-2 bg-slate-400 rounded-full" style={{ animationDelay: `${i * 0.2}s` }} />
          ))}
        </div>
      </div>
    </div>
  );
}

// Chat mit dem KI-Assistenten (Phasen 3 und 4, Backend: AssistantChat). Er schlägt in Kalender, Aufgaben, Einkauf,
// Essensplan, Wetter und Müllabfuhr nach und sieht dabei nur, was die angemeldete Person sieht. Ändern soll sich etwas,
// bereitet er einen Entwurf vor (DraftCard), den die Person selbst übernimmt. Der Verlauf ist pro Person gespeichert und
// erscheint auch auf anderen Geräten (Live-Aktualisierung, Bereich "assistant").
export default function AIAssistant(_props: Props) {
  const me = useMe();
  const { can } = useAuth();
  const mayUse = can('sprachassistent', 'ansehen', 'eigen');
  const [status, setStatus] = useState<AssistantStatus | null>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [input, setInput] = useState('');
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  // Während eine Frage läuft, soll kein Neuladen die noch nicht gespeicherte Frage überschreiben
  const waitingRef = useRef(false);

  const reload = useCallback(async () => {
    if (!mayUse || waitingRef.current) return;
    try {
      const [s, c] = await Promise.all([getAssistantStatus(), getConversation()]);
      if (waitingRef.current) return;
      setStatus(s);
      setTurns(c.turns);
    } catch (err) {
      setError(errorText(err));
    }
  }, [mayUse]);

  useEffect(() => { void reload(); }, [reload]);
  useAutoRefresh(reload, ['assistant']);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [turns, waiting]);

  const send = async (text: string) => {
    const question = text.trim();
    if (!question || waiting) return;
    const pending: ChatTurn = { role: 'user', text: question, at: new Date().toISOString(), sources: [], drafts: [] };
    setError(null);
    setInput('');
    setTurns(t => [...t, pending]);
    setWaiting(true);
    waitingRef.current = true;
    try {
      const answer = await askAssistant(question);
      setTurns(t => [...t, answer]);
    } catch (err) {
      // Nichts wurde gespeichert: Frage zurück ins Eingabefeld, damit man es noch einmal versuchen kann
      setTurns(t => t.filter(turn => turn !== pending));
      setInput(question);
      setError(errorText(err));
    } finally {
      waitingRef.current = false;
      setWaiting(false);
    }
  };

  // Nach Übernehmen oder Verwerfen den Entwurf an seiner Antwort ersetzen
  const replaceDraft = (updated: Draft) =>
    setTurns(ts => ts.map(t => ({ ...t, drafts: t.drafts.map(d => (d.id === updated.id ? updated : d)) })));

  const startOver = async () => {
    setError(null);
    try {
      await clearConversation();
      setTurns([]);
    } catch (err) {
      setError(errorText(err));
    }
  };

  if (!mayUse) {
    return (
      <div className="p-4 lg:p-6 max-w-[900px] mx-auto">
        <p className="text-sm text-slate-500 bg-white border border-slate-100 rounded-2xl p-5">
          Der KI-Assistent steht Gästen nicht zur Verfügung.
        </p>
      </div>
    );
  }

  const notConfigured = status !== null && !status.configured;
  const switchedOff = status !== null && status.configured && !status.enabled;
  const unavailable = notConfigured || switchedOff;

  return (
    <div className="flex flex-col h-full max-h-[calc(100vh-120px)]">
      {/* Kopf */}
      <div className="flex-shrink-0 px-4 lg:px-6 py-4 bg-gradient-to-r from-[#EFF6FF] to-[#F0FDFA] border-b border-slate-100">
        <div className="flex items-center gap-3 max-w-[900px] mx-auto">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center flex-shrink-0">
            <SparklesIcon size={22} className="text-white" />
          </div>
          <div className="min-w-0">
            <div className="font-bold text-slate-800">FamilyHub KI</div>
            <div className="text-xs text-slate-500">
              Schlägt in Kalender, Aufgaben, Einkauf, Essensplan, Wetter, Müllabfuhr und Nachrichten nach und bereitet
              Änderungen als Entwurf vor. Sieht nur, was du siehst; übernehmen musst du selbst.
            </div>
          </div>
          <button type="button" onClick={startOver} disabled={turns.length === 0 || waiting}
            className="ml-auto flex-shrink-0 text-xs bg-white border border-slate-200 text-slate-600 px-3 py-1.5 rounded-full font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm disabled:opacity-40">
            Neues Gespräch
          </button>
        </div>
      </div>

      {/* Nachrichten */}
      <div className="flex-1 overflow-y-auto px-4 lg:px-6 py-5">
        <div className="max-w-[900px] mx-auto space-y-5">
          {notConfigured && (
            <p role="alert" className="text-sm bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] rounded-xl px-4 py-3">
              Der KI-Assistent ist noch nicht eingerichtet: Auf dem Server fehlt der API-Schlüssel von Groq
              (siehe README, „KI-Assistent einrichten“).
            </p>
          )}

          {switchedOff && (
            <p role="alert" className="text-sm bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] rounded-xl px-4 py-3">
              Der KI-Assistent ist gerade abgeschaltet. Ein Administrator kann ihn unter „Familie“ wieder einschalten.
            </p>
          )}

          {turns.length === 0 && !waiting && !unavailable && (
            <div className="text-center py-6 space-y-4">
              <p className="text-sm text-slate-600">Hallo {me.name}! Was möchtest du wissen?</p>
              <div className="flex flex-wrap justify-center gap-2">
                {SUGGESTIONS.map(s => (
                  <button key={s} type="button" onClick={() => void send(s)}
                    className="text-xs bg-[#EFF6FF] text-[#2563EB] border border-[#BFDBFE] px-3 py-1.5 rounded-full font-medium hover:bg-[#DBEAFE] transition-colors">
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}

          {turns.map((turn, i) => (
            <div key={`${turn.at}-${i}`} className={`flex items-start gap-3 ${turn.role !== 'assistant' ? 'flex-row-reverse' : ''}`}>
              {turn.role === 'assistant' ? (
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center flex-shrink-0">
                  <SparklesIcon size={14} className="text-white" />
                </div>
              ) : (
                <div className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
                  style={{ backgroundColor: me.color }}>
                  {me.name.charAt(0).toUpperCase()}
                </div>
              )}
              <div className={`flex flex-col gap-1 ${turn.drafts.length > 0 ? 'max-w-[92%] sm:max-w-[75%]' : 'max-w-[75%]'} ${turn.role !== 'assistant' ? 'items-end' : 'items-start'}`}>
                {turn.role === 'inbound' && (
                  <span className="text-[11px] font-medium text-slate-500">Per {turn.sources.join(', ')} an FamilyHub geschickt</span>
                )}
                <div className={`px-4 py-3 rounded-2xl text-sm leading-relaxed whitespace-pre-line ${
                  turn.role === 'user'
                    ? 'bg-[#2563EB] text-white rounded-tr-sm'
                    : turn.role === 'inbound'
                      ? 'bg-[#F0FDF4] border border-[#BBF7D0] text-slate-800 rounded-tr-sm'
                      : 'bg-white border border-slate-100 text-slate-800 rounded-tl-sm shadow-sm'
                }`}>
                  {turn.text}
                </div>
                {turn.drafts.map(draft => <DraftCard key={draft.id} draft={draft} onChange={replaceDraft} />)}
                <span className="text-[10px] text-slate-500">
                  {timeOf(turn.at)}
                  {turn.role === 'assistant' && turn.sources.length > 0 && ` · Nachgeschlagen in: ${turn.sources.join(', ')}`}
                </span>
              </div>
            </div>
          ))}

          {waiting && <TypingIndicator />}
          <div ref={bottomRef} />
        </div>
      </div>

      {/* Eingabe */}
      <div className="flex-shrink-0 px-4 lg:px-6 py-4 bg-white border-t border-slate-100">
        <div className="max-w-[900px] mx-auto">
          {error && <p role="alert" className="mb-2 text-sm text-[#DC2626]">{error}</p>}
          <div className="flex items-end gap-2">
            <textarea
              className="flex-1 border border-slate-200 rounded-2xl px-4 py-3 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 resize-none bg-[#F8FAFC] max-h-28 disabled:opacity-50"
              placeholder="Frag nach Terminen, Aufgaben, Einkauf, Essen, Wetter oder Müll, oder lass etwas vorbereiten …"
              rows={1}
              maxLength={1000}
              value={input}
              disabled={unavailable}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  void send(input);
                }
              }}
              aria-label="Frage an den KI-Assistenten"
            />
            <button type="button" onClick={() => void send(input)} disabled={!input.trim() || waiting || unavailable}
              className="w-11 h-11 rounded-2xl bg-[#2563EB] text-white flex items-center justify-center hover:bg-[#1D4ED8] transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0 shadow-sm"
              aria-label="Frage senden">
              <SendIcon size={18} />
            </button>
          </div>
          <p className="text-[10px] text-slate-500 mt-2 text-center">
            Die KI kann sich irren. Prüfe Entwürfe, bevor du sie übernimmst.
          </p>
        </div>
      </div>
    </div>
  );
}
