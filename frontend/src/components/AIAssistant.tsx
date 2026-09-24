import { useState, useRef, useEffect } from 'react';
import { MicIcon, SendIcon, SparklesIcon, XIcon, CheckIcon } from './Icons';
import { useAuth, useMe } from '../auth/AuthContext';
import { Suggestion, createSuggestion, decide } from '../roles';

interface Props { onNavigate: (p: any) => void; }

interface PointsAward {
  memberName: string;
  points: number;
  reason: string;
}

interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  time: string;
  suggestions?: string[];
  // Freigabe-Workflow (README Abschnitt 5.1): Aktionen mit Wirkung (hier:
  // Punkte vergeben) fuehrt der Assistent nicht mehr direkt aus, sondern
  // legt einen Vorschlag an, der von einem Administrator entschieden wird.
  approval?: Suggestion<PointsAward>;
}

const INITIAL_MESSAGES: Message[] = [
  {
    id: 1,
    role: 'assistant',
    content: "Hi Sarah! 👋 I'm your FamilyHub AI assistant. I can help you manage your family's schedule, tasks, meals, and more.\n\nHere's what's happening today:\n- **Lucas** hasn't started Math homework yet (due tonight)\n- **Emma's** soccer practice is at 4:30pm — it's 12 km from your current location\n- **Lily** has piano lesson tomorrow at 3:30pm\n- You have a **schedule conflict** on Sep 24 between piano lesson and parent-teacher conference",
    time: '09:00',
    suggestions: [
      'Remind Lucas about homework',
      'Add soccer pickup to calendar',
      'Resolve schedule conflict',
      "What's for dinner tonight?",
    ],
  },
];

const AI_RESPONSES: Record<string, { content: string; suggestions?: string[] }> = {
  'remind lucas': {
    content: "✅ Done! I've sent Lucas a push notification reminding him about his Math homework (Chapter 5, exercises 1–20). He has until tonight to complete it.\n\nWould you like me to:\n- Set an escalating reminder if he hasn't started by 4pm?\n- Award bonus points if he finishes before dinner?",
    suggestions: ['Set 4pm reminder', 'Award 5 bonus points', 'Check his progress'],
  },
  'schedule conflict': {
    content: "I found a conflict on **September 24th**:\n\n⚠️ **3:30–4:30pm** — Lily's Gymnastics\n⚠️ **2:00–3:00pm** — Parent-teacher conference (Lincoln Elementary)\n\nThese don't actually overlap! However, travel time (10 min) from the school means you might be cutting it close.\n\nMy suggestion: Reschedule parent-teacher to 1pm so you have comfortable buffer time.\n\nShall I check teacher availability?",
    suggestions: ['Check 1pm availability', 'Ask Mike to cover pickup', 'View calendar'],
  },
  'dinner tonight': {
    content: "Tonight's dinner is **Spaghetti Bolognese** 🍝\n\nGood news — you have all the ingredients except:\n- ✅ Ground beef\n- ✅ Canned tomatoes\n- ✅ Pasta\n- ❌ **Fresh basil** (I've added it to your shopping list)\n\nEstimated prep time: 35 minutes. Want me to find an easier 20-min version?",
    suggestions: ['Find quicker recipe', 'Add basil to shopping', 'Set dinner reminder'],
  },
  'soccer': {
    content: "Emma's soccer practice is at **City Sports Complex** starting at **4:30pm**.\n\n🚗 Travel time from home: ~12 minutes\n📍 I recommend leaving by **4:15pm**\n\nI've added a departure reminder for 4:10pm. Lucas's school pickup is at 3:00pm — you'll have plenty of time.\n\nNote: Rain is forecast at 5pm so bring a jacket for Emma! 🌧️",
    suggestions: ['Set departure reminder', 'Check pickup route', 'Message Emma'],
  },
  'default': {
    content: "I've got that! Let me help you with that. Here's what I found based on your family's schedule and preferences.\n\nIs there anything specific you'd like me to look into further?",
    suggestions: ['Show family schedule', 'Check tasks', 'Plan meals for week'],
  },
};

function VoiceWave({ active }: { active: boolean }) {
  if (!active) return null;
  return (
    <div className="flex items-center justify-center gap-0.5 h-8">
      {Array.from({ length: 8 }).map((_, i) => (
        <div
          key={i}
          className="wave-bar w-1 bg-[#2563EB] rounded-full"
          style={{ height: '100%' }}
        />
      ))}
    </div>
  );
}

function TypingIndicator() {
  return (
    <div className="flex items-start gap-3">
      <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center flex-shrink-0">
        <SparklesIcon size={14} className="text-white" />
      </div>
      <div className="bg-white border border-slate-100 rounded-2xl rounded-tl-sm px-4 py-3">
        <div className="flex items-center gap-1">
          {[0,1,2].map(i => (
            <div
              key={i}
              className="typing-dot w-2 h-2 bg-slate-400 rounded-full"
              style={{ animationDelay: `${i * 0.2}s` }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

export default function AIAssistant({ onNavigate }: Props) {
  const [messages, setMessages] = useState<Message[]>(INITIAL_MESSAGES);
  const [input, setInput] = useState('');
  const [listening, setListening] = useState(false);
  const [typing, setTyping] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const me = useMe();
  const { can } = useAuth();
  const mayAwardPoints = can('punkte', 'freigeben', 'familie');

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, typing]);

  const getResponse = (text: string) => {
    const lower = text.toLowerCase();
    if (lower.includes('lucas') || lower.includes('remind')) return AI_RESPONSES['remind lucas'];
    if (lower.includes('conflict') || lower.includes('schedule')) return AI_RESPONSES['schedule conflict'];
    if (lower.includes('dinner') || lower.includes('meal') || lower.includes('food')) return AI_RESPONSES['dinner tonight'];
    if (lower.includes('soccer') || lower.includes('emma') || lower.includes('pickup')) return AI_RESPONSES['soccer'];
    return AI_RESPONSES['default'];
  };

  const sendMessage = (text: string) => {
    if (!text.trim()) return;
    const userMsg: Message = {
      id: Date.now(),
      role: 'user',
      content: text,
      time: new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }),
    };
    setMessages(ms => [...ms, userMsg]);
    setInput('');
    setTyping(true);

    const lower = text.toLowerCase();
    // Punkte vergeben ist eine privilegierte Aktion (Berechtigungsmatrix,
    // Abschnitt 4): der Assistent fuehrt sie nicht mehr direkt aus, sondern
    // legt einen Vorschlag an (README Abschnitt 5.1, Freigabe-Workflow).
    const isAwardPoints = lower.includes('award') && lower.includes('point');

    setTimeout(() => {
      setTyping(false);
      if (isAwardPoints) {
        const suggestion = createSuggestion<PointsAward>(
          'punkte',
          { memberName: 'Lucas', points: 5, reason: 'Math-Hausaufgabe rechtzeitig fertig' },
          'KI-Agent',
        );
        const resolved = mayAwardPoints ? decide(suggestion, me.name, true) : suggestion;
        const aiMsg: Message = {
          id: Date.now() + 1,
          role: 'assistant',
          content: '',
          time: new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }),
          approval: resolved,
        };
        setMessages(ms => [...ms, aiMsg]);
        return;
      }
      const resp = getResponse(text);
      const aiMsg: Message = {
        id: Date.now() + 1,
        role: 'assistant',
        content: resp.content,
        time: new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }),
        suggestions: resp.suggestions,
      };
      setMessages(ms => [...ms, aiMsg]);
    }, 1200);
  };

  const decideApproval = (messageId: number, approve: boolean) => {
    if (!mayAwardPoints) return;
    setMessages(ms => ms.map(m => {
      if (m.id !== messageId || !m.approval || m.approval.status !== 'vorschlag') return m;
      return { ...m, approval: decide(m.approval, me.name, approve) };
    }));
  };

  const toggleListening = () => {
    setListening(l => {
      if (!l) {
        setTimeout(() => {
          setListening(false);
          sendMessage("What's on Emma's schedule today?");
        }, 2500);
      }
      return !l;
    });
  };

  const renderContent = (content: string) => {
    return content.split('\n').map((line, i) => {
      if (line.startsWith('**') && line.endsWith('**')) {
        return <p key={i} className="font-bold text-slate-800">{line.slice(2, -2)}</p>;
      }
      const parts = line.split(/(\*\*[^*]+\*\*)/g);
      return (
        <p key={i} className={`${line.startsWith('- ') ? 'pl-2' : ''} ${line === '' ? 'h-2' : ''}`}>
          {parts.map((part, j) =>
            part.startsWith('**') && part.endsWith('**')
              ? <strong key={j}>{part.slice(2, -2)}</strong>
              : part
          )}
        </p>
      );
    });
  };

  return (
    <div className="flex flex-col h-full max-h-[calc(100vh-120px)]">
      {/* AI header */}
      <div className="flex-shrink-0 px-4 lg:px-6 py-4 bg-gradient-to-r from-[#EFF6FF] to-[#F0FDFA] border-b border-slate-100">
        <div className="flex items-center gap-3 max-w-[900px] mx-auto">
          <div className="relative">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center">
              <SparklesIcon size={22} className="text-white" />
            </div>
            <div className="absolute bottom-0 right-0 w-3 h-3 bg-[#22C55E] rounded-full border-2 border-white" />
          </div>
          <div>
            <div className="font-bold text-slate-800">FamilyHub AI</div>
            <div className="text-xs text-slate-500">Your family's intelligent assistant · Always on</div>
          </div>
          <div className="ml-auto flex flex-wrap gap-2">
            {['📅 Add event', '✅ Create task', '🛒 Shopping', '🍽️ Meal idea'].map(cap => (
              <button
                key={cap}
                onClick={() => sendMessage(`Help me ${cap.split(' ').slice(1).join(' ')}`)}
                className="text-xs bg-white border border-slate-200 text-slate-600 px-3 py-1.5 rounded-full font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm hidden sm:block"
              >
                {cap}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 lg:px-6 py-5 space-y-5">
        <div className="max-w-[900px] mx-auto space-y-5">
          {messages.map(msg => (
            <div key={msg.id} className={`flex items-start gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
              {/* Avatar */}
              {msg.role === 'assistant' ? (
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center flex-shrink-0">
                  <SparklesIcon size={14} className="text-white" />
                </div>
              ) : (
                <div
                  className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
                  style={{ backgroundColor: me.color }}
                >
                  {me.name.charAt(0).toUpperCase()}
                </div>
              )}

              <div className={`flex flex-col gap-2 max-w-[75%] ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                <div
                  className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
                    msg.role === 'user'
                      ? 'bg-[#2563EB] text-white rounded-tr-sm'
                      : 'bg-white border border-slate-100 text-slate-800 rounded-tl-sm shadow-sm'
                  }`}
                >
                  {msg.approval ? (
                    <div className="space-y-2 min-w-[220px]">
                      <p className="font-semibold text-slate-800">
                        🎁 Vorschlag: {msg.approval.payload.points} Bonuspunkte für {msg.approval.payload.memberName}
                      </p>
                      <p className="text-xs text-slate-500">{msg.approval.payload.reason}</p>
                      {msg.approval.status === 'vorschlag' && can('punkte', 'freigeben', 'familie') && (
                        <div className="flex gap-2 pt-1">
                          <button
                            onClick={() => decideApproval(msg.id, true)}
                            className="flex items-center gap-1 text-xs bg-[#22C55E] text-white px-3 py-1.5 rounded-full font-semibold hover:bg-[#16A34A] transition-colors"
                          >
                            <CheckIcon size={11} strokeWidth={3} /> Freigeben
                          </button>
                          <button
                            onClick={() => decideApproval(msg.id, false)}
                            className="flex items-center gap-1 text-xs bg-slate-100 text-slate-600 px-3 py-1.5 rounded-full font-semibold hover:bg-slate-200 transition-colors"
                          >
                            <XIcon size={11} strokeWidth={3} /> Ablehnen
                          </button>
                        </div>
                      )}
                      {msg.approval.status === 'vorschlag' && !can('punkte', 'freigeben', 'familie') && (
                        <p className="text-xs text-[#F59E0B] font-semibold pt-1">Wartet auf Freigabe durch einen Elternteil</p>
                      )}
                      {msg.approval.status === 'freigegeben' && (
                        <p className="text-xs text-[#16A34A] font-semibold pt-1">Freigegeben von {msg.approval.decidedBy}</p>
                      )}
                      {msg.approval.status === 'abgelehnt' && (
                        <p className="text-xs text-slate-400 font-semibold pt-1">Abgelehnt von {msg.approval.decidedBy}</p>
                      )}
                    </div>
                  ) : (
                    <div className="space-y-1">
                      {renderContent(msg.content)}
                    </div>
                  )}
                </div>

                <span className="text-[10px] text-slate-400">{msg.time}</span>

                {/* Suggestion chips */}
                {msg.suggestions && msg.role === 'assistant' && (
                  <div className="flex flex-wrap gap-2 mt-1">
                    {msg.suggestions.map(s => (
                      <button
                        key={s}
                        onClick={() => sendMessage(s)}
                        className="text-xs bg-[#EFF6FF] text-[#2563EB] border border-[#BFDBFE] px-3 py-1.5 rounded-full font-medium hover:bg-[#DBEAFE] transition-colors"
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}

          {typing && <TypingIndicator />}
          <div ref={bottomRef} />
        </div>
      </div>

      {/* Input area */}
      <div className="flex-shrink-0 px-4 lg:px-6 py-4 bg-white border-t border-slate-100">
        <div className="max-w-[900px] mx-auto">
          {/* Voice indicator */}
          {listening && (
            <div className="mb-3 flex items-center gap-3 bg-[#EFF6FF] rounded-xl px-4 py-2.5 border border-[#BFDBFE]">
              <div className="relative">
                <div className="w-4 h-4 rounded-full bg-[#EF4444] pulse-ring" />
                <div className="w-4 h-4 rounded-full bg-[#EF4444]" />
              </div>
              <VoiceWave active={listening} />
              <span className="text-sm text-[#2563EB] font-medium flex-1">Listening...</span>
              <button onClick={() => setListening(false)}>
                <XIcon size={16} className="text-slate-400" />
              </button>
            </div>
          )}

          <div className="flex items-end gap-2">
            <div className="flex-1 relative">
              <textarea
                className="w-full border border-slate-200 rounded-2xl pl-4 pr-4 py-3 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 resize-none bg-[#F8FAFC] max-h-28"
                placeholder="Ask me anything about your family's schedule, tasks, meals..."
                rows={1}
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendMessage(input);
                  }
                }}
                aria-label="Message input"
              />
            </div>

            {/* Voice button */}
            <button
              onClick={toggleListening}
              className={`w-11 h-11 rounded-2xl flex items-center justify-center transition-all flex-shrink-0 ${
                listening
                  ? 'bg-[#EF4444] text-white shadow-lg shadow-red-200'
                  : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
              }`}
              aria-label={listening ? 'Stop listening' : 'Start voice input'}
            >
              <MicIcon size={18} />
            </button>

            {/* Send button */}
            <button
              onClick={() => sendMessage(input)}
              disabled={!input.trim()}
              className="w-11 h-11 rounded-2xl bg-[#2563EB] text-white flex items-center justify-center hover:bg-[#1D4ED8] transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0 shadow-sm"
              aria-label="Send message"
            >
              <SendIcon size={18} />
            </button>
          </div>

          <p className="text-[10px] text-slate-400 mt-2 text-center">
            AI can make mistakes. Always verify important family schedules.
          </p>
        </div>
      </div>
    </div>
  );
}
