import { useCallback, useEffect, useState } from 'react';
import Dashboard from './components/Dashboard';
import CalendarPage from './components/CalendarPage';
import Tasks from './components/Tasks';
import Rewards from './components/Rewards';
import Shopping from './components/Shopping';
import MealPlanning from './components/MealPlanning';
import AIAssistant from './components/AIAssistant';
import Messenger from './components/Messenger';
import Profiles from './components/Profiles';
import {
  HomeIcon, CalendarIcon, CheckSquareIcon, StarIcon,
  ShoppingCartIcon, UtensilsIcon, SparklesIcon, MessageIcon,
  UsersIcon, BellIcon, MenuIcon, XIcon, SettingsIcon, MicIcon,
} from './components/Icons';
import { NOTIFICATIONS } from './components/data';
import { useAuth, useMe } from './auth/AuthContext';
import { useCalendarData } from './calendar/CalendarDataContext';
import { formatLongDate, startOfToday } from './calendar/dates';
import { useTaskData } from './tasks/TaskDataContext';
import NewPointsNotice from './points/NewPointsNotice';
import { googleReturnFromUrl } from './google/api';
import { useRewardData } from './rewards/RewardDataContext';
import { useShoppingData } from './shopping/ShoppingDataContext';
import { useMealData } from './meals/MealDataContext';
import { ROLE_NAMES } from './roles';
import { FocusContext, type Focus } from './navigation/focus';

type Page = 'dashboard' | 'calendar' | 'tasks' | 'rewards' | 'shopping' | 'meals' | 'assistant' | 'messenger' | 'profiles';

const NAV = [
  { id: 'dashboard' as Page, label: 'Übersicht', Icon: HomeIcon },
  { id: 'calendar' as Page, label: 'Kalender', Icon: CalendarIcon },
  { id: 'tasks' as Page, label: 'Aufgaben', Icon: CheckSquareIcon },
  { id: 'rewards' as Page, label: 'Belohnungen', Icon: StarIcon },
  { id: 'shopping' as Page, label: 'Einkauf', Icon: ShoppingCartIcon },
  { id: 'meals' as Page, label: 'Essensplan', Icon: UtensilsIcon },
  { id: 'assistant' as Page, label: 'KI-Assistent', Icon: SparklesIcon },
  { id: 'messenger' as Page, label: 'Nachrichten', Icon: MessageIcon },
  { id: 'profiles' as Page, label: 'Familie', Icon: UsersIcon },
];

const PAGE_TITLES: Record<Page, string> = {
  dashboard: 'Übersicht',
  calendar: 'Familienkalender',
  tasks: 'Aufgaben',
  rewards: 'Belohnungen',
  shopping: 'Einkaufsliste',
  meals: 'Essensplanung',
  assistant: 'KI-Assistent',
  messenger: 'Nachrichten',
  profiles: 'Familie & Profile',
};

export default function App() {
  // Nach der Rückkehr von Google (?google=…) die Profilseite mit der Google-Karte öffnen
  const [page, setPage] = useState<Page>(() => (googleReturnFromUrl() ? 'profiles' : 'dashboard'));
  // Parameter danach aus der Adresse nehmen, damit die Meldung beim Neuladen nicht wieder erscheint.
  // Effekte der Kinder laufen vorher, die Google-Karte hat ihn da schon gelesen.
  useEffect(() => {
    if (googleReturnFromUrl()) window.history.replaceState(null, '', window.location.pathname);
  }, []);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const me = useMe();
  const { roleName, logout, can } = useAuth();
  const { members } = useCalendarData();
  const { tasks } = useTaskData();
  // Zähler an "Tasks": für Administratoren die wartenden Bestätigungen, sonst die eigenen offenen Aufgaben
  const mayConfirmTasks = can('punkte', 'freigeben', 'familie');
  const taskBadge = mayConfirmTasks
    ? tasks.filter(t => t.status === 'done' && t.points > 0).length
    : tasks.filter(t => t.assigneeId === me.id && t.status !== 'done' && t.status !== 'confirmed').length;
  // Zähler an "Rewards": für Administratoren die Einlösungen, die auf Genehmigung warten
  const { redemptions } = useRewardData();
  const mayDecideRewards = can('punkte', 'freigeben', 'familie');
  const rewardBadge = mayDecideRewards ? redemptions.filter(r => r.status === 'pending').length : 0;
  // Zähler an "Shopping": für Administratoren die Vorschläge der Kinder
  const { items: shoppingItems } = useShoppingData();
  const shoppingBadge = can('einkauf', 'freigeben', 'familie')
    ? shoppingItems.filter(i => i.status === 'proposed').length : 0;
  // Zähler an "Essensplan": für Administratoren die Essenswünsche der Kinder
  const { wishes: mealWishes } = useMealData();
  const mealBadge = can('essen', 'freigeben', 'familie') ? mealWishes.length : 0;
  const initial = me.name.charAt(0).toUpperCase();

  const unreadCount = NOTIFICATIONS.filter(n => !n.read).length;

  // Sprungziel für die nächste Seite (z. B. Termin aus der Übersicht); ein normaler Seitenwechsel löscht es
  const [focus, setFocus] = useState<Focus | null>(null);
  const clearFocus = useCallback(() => setFocus(null), []);

  const navigate = (p: Page, target?: Focus) => {
    setPage(p);
    setFocus(target ?? null);
    setSidebarOpen(false);
  };

  const PageComponent = {
    dashboard: Dashboard,
    calendar: CalendarPage,
    tasks: Tasks,
    rewards: Rewards,
    shopping: Shopping,
    meals: MealPlanning,
    assistant: AIAssistant,
    messenger: Messenger,
    profiles: Profiles,
  }[page];

  return (
    <div className="flex h-screen bg-[#F8FAFC] overflow-hidden">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/30 z-40 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`
          fixed lg:relative inset-y-0 left-0 z-50 flex flex-col
          bg-white border-r border-slate-100
          transition-all duration-300 ease-in-out
          ${sidebarOpen ? 'w-64 translate-x-0' : 'w-64 -translate-x-full lg:translate-x-0'}
          lg:w-[220px] xl:w-60
          shadow-lg lg:shadow-none
        `}
      >
        {/* Logo */}
        <div className="flex items-center gap-3 px-5 py-5 border-b border-slate-100">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center flex-shrink-0">
            <span className="text-white text-lg">🏠</span>
          </div>
          <div>
            <div className="font-bold text-[#0F172A] text-base leading-tight">FamilyHub</div>
            <div className="text-[10px] text-slate-400 font-medium tracking-wide uppercase">AI Organizer</div>
          </div>
          <button
            className="ml-auto lg:hidden text-slate-400 hover:text-slate-600 p-1"
            onClick={() => setSidebarOpen(false)}
            aria-label="Menü schließen"
          >
            <XIcon size={18} />
          </button>
        </div>

        {/* Family members */}
        <div className="px-4 pt-4 pb-3">
          <div className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-2 px-1">Familie</div>
          <div className="flex gap-1.5 flex-wrap">
            {members.map(m => {
              const self = m.id === me.id;
              return (
                <div
                  key={m.id}
                  title={`${m.name} · ${ROLE_NAMES[m.effectiveRole]}${self ? ' (du)' : ''}`}
                  className={`w-8 h-8 rounded-full flex items-center justify-center text-[10px] font-bold text-white flex-shrink-0 ring-2 ${self ? 'ring-[#2563EB] scale-110' : 'ring-white'}`}
                  style={{ backgroundColor: m.color }}
                >
                  {m.initials[0]}
                </div>
              );
            })}
          </div>
          <p className="text-[10px] text-slate-400 mt-2 px-1">
            Angemeldet als <span className="font-semibold text-slate-500">{me.name}</span> · Rolle: {roleName}
          </p>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 py-2 overflow-y-auto scrollbar-hide">
          {NAV.map(({ id, label, Icon }) => {
            const active = page === id;
            return (
              <button
                key={id}
                onClick={() => navigate(id)}
                className={`
                  nav-item w-full flex items-center gap-3 px-3 py-2.5 rounded-xl mb-0.5 text-left
                  transition-all duration-150
                  ${active
                    ? 'bg-[#EFF6FF] text-[#2563EB] font-semibold'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900 font-medium'
                  }
                `}
                aria-current={active ? 'page' : undefined}
              >
                <span className={`flex-shrink-0 ${active ? 'text-[#2563EB]' : 'text-slate-400'}`}>
                  <Icon size={18} />
                </span>
                <span className="text-sm">{label}</span>
                {id === 'messenger' && (
                  <span className="ml-auto w-5 h-5 rounded-full bg-[#EF4444] text-white text-[10px] font-bold flex items-center justify-center">3</span>
                )}
                {id === 'tasks' && taskBadge > 0 && (
                  <span className="ml-auto w-5 h-5 rounded-full bg-[#F97316] text-white text-[10px] font-bold flex items-center justify-center"
                    title={mayConfirmTasks ? 'Aufgaben warten auf Bestätigung' : 'Offene Aufgaben'}>
                    {taskBadge}<span className="sr-only">{mayConfirmTasks ? ' warten auf Bestätigung' : ' offen'}</span>
                  </span>
                )}
                {id === 'shopping' && shoppingBadge > 0 && (
                  <span className="ml-auto w-5 h-5 rounded-full bg-[#22C55E] text-white text-[10px] font-bold flex items-center justify-center"
                    title="Vorschläge für die Einkaufsliste">
                    {shoppingBadge}<span className="sr-only"> Vorschläge für die Einkaufsliste</span>
                  </span>
                )}
                {id === 'meals' && mealBadge > 0 && (
                  <span className="ml-auto w-5 h-5 rounded-full bg-[#14B8A6] text-white text-[10px] font-bold flex items-center justify-center"
                    title="Essenswünsche der Kinder">
                    {mealBadge}<span className="sr-only"> Essenswünsche der Kinder</span>
                  </span>
                )}
                {id === 'rewards' && rewardBadge > 0 && (
                  <span className="ml-auto w-5 h-5 rounded-full bg-[#8B5CF6] text-white text-[10px] font-bold flex items-center justify-center"
                    title="Einlösungen warten auf Genehmigung">
                    {rewardBadge}<span className="sr-only"> Einlösungen warten auf Genehmigung</span>
                  </span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Bottom: Settings + User */}
        <div className="px-3 pb-4 border-t border-slate-100 pt-3">
          <button
            className="nav-item w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-slate-500 hover:bg-slate-50 hover:text-slate-800 font-medium transition-all"
            onClick={() => navigate('profiles')}
          >
            <SettingsIcon size={18} className="flex-shrink-0 text-slate-400" />
            <span className="text-sm">Einstellungen</span>
          </button>
          <div className="flex items-center gap-2 px-3 py-2 mt-1 rounded-xl bg-slate-50">
            <div
              className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
              style={{ backgroundColor: me.color }}
            >
              {initial}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-semibold text-slate-800 truncate">{me.name}</div>
              <div className="text-[10px] text-slate-400">{roleName}</div>
            </div>
            <button
              onClick={logout}
              className="text-[11px] font-semibold text-slate-500 hover:text-[#DC2626] px-2 py-1 rounded-lg hover:bg-white transition-colors"
            >
              Abmelden
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top header */}
        <header className="flex-shrink-0 bg-white border-b border-slate-100 px-4 lg:px-6 py-3.5 flex items-center gap-4">
          {/* Mobile hamburger */}
          <button
            className="lg:hidden text-slate-500 hover:text-slate-800 p-1.5 rounded-lg hover:bg-slate-100 transition-colors"
            onClick={() => setSidebarOpen(true)}
            aria-label="Menü öffnen"
          >
            <MenuIcon size={20} />
          </button>

          <div>
            <h1 className="text-lg font-bold text-[#0F172A] leading-tight">{PAGE_TITLES[page]}</h1>
            <p className="text-xs text-slate-400 hidden sm:block">
              {formatLongDate(startOfToday())}
            </p>
          </div>

          <div className="ml-auto flex items-center gap-2">
            {/* AI quick access */}
            <button
              onClick={() => navigate('assistant')}
              className="hidden sm:flex items-center gap-2 bg-gradient-to-r from-[#2563EB] to-[#14B8A6] text-white text-sm font-medium px-3.5 py-1.5 rounded-full hover:shadow-md hover:shadow-blue-200 transition-all"
            >
              <SparklesIcon size={14} />
              <span>KI fragen</span>
            </button>

            {/* Notifications */}
            <div className="relative">
              <button
                className="relative p-2 rounded-xl text-slate-500 hover:bg-slate-100 hover:text-slate-800 transition-colors"
                onClick={() => setShowNotifications(!showNotifications)}
                aria-label={`Benachrichtigungen (${unreadCount} ungelesen)`}
              >
                <BellIcon size={20} />
                {unreadCount > 0 && (
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-[#EF4444] rounded-full" />
                )}
              </button>

              {showNotifications && (
                <div className="absolute right-0 top-full mt-2 w-80 bg-white rounded-2xl shadow-xl border border-slate-100 z-50 overflow-hidden">
                  <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
                    <span className="font-semibold text-sm text-slate-800">Benachrichtigungen</span>
                    <span className="text-xs text-[#2563EB] font-medium cursor-pointer">Alle als gelesen markieren</span>
                  </div>
                  <div className="max-h-80 overflow-y-auto">
                    {NOTIFICATIONS.map(n => (
                      <div key={n.id} className={`px-4 py-3 border-b border-slate-50 hover:bg-slate-50 transition-colors cursor-pointer ${!n.read ? 'bg-[#EFF6FF]/40' : ''}`}>
                        <div className="flex items-start gap-2.5">
                          <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${!n.read ? 'bg-[#2563EB]' : 'bg-slate-200'}`} />
                          <div>
                            <p className="text-xs text-slate-700 leading-relaxed">{n.message}</p>
                            <p className="text-[10px] text-slate-400 mt-0.5">{n.time}</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Avatar */}
            <button
              className="w-9 h-9 rounded-full flex items-center justify-center text-white text-sm font-bold ring-2 ring-[#EFF6FF] hover:ring-[#2563EB] transition-all"
              style={{ backgroundColor: me.color }}
              aria-label="Mein Profil"
              onClick={() => navigate('profiles')}
            >
              {initial}
            </button>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto">
          <FocusContext.Provider value={{ focus, clear: clearFocus }}>
            <PageComponent onNavigate={navigate} />
          </FocusContext.Provider>
        </main>
        <NewPointsNotice />

        {/* Mobile bottom nav */}
        <nav className="lg:hidden flex-shrink-0 bg-white border-t border-slate-100 px-2 py-1 safe-area-bottom">
          <div className="flex justify-around">
            {NAV.slice(0, 5).map(({ id, label, Icon }) => {
              const active = page === id;
              return (
                <button
                  key={id}
                  onClick={() => navigate(id)}
                  className="flex flex-col items-center gap-0.5 px-2 py-1.5 rounded-xl min-w-[48px]"
                  aria-current={active ? 'page' : undefined}
                >
                  <span className={active ? 'text-[#2563EB]' : 'text-slate-400'}>
                    <Icon size={20} />
                  </span>
                  <span className={`text-[9px] font-medium ${active ? 'text-[#2563EB]' : 'text-slate-400'}`}>{label}</span>
                </button>
              );
            })}
            <button
              onClick={() => setSidebarOpen(true)}
              className="flex flex-col items-center gap-0.5 px-2 py-1.5 rounded-xl min-w-[48px]"
            >
              <span className="text-slate-400"><MenuIcon size={20} /></span>
              <span className="text-[9px] font-medium text-slate-400">Mehr</span>
            </button>
          </div>
        </nav>
      </div>

      {/* Floating voice assistant button */}
      <button
        onClick={() => navigate('assistant')}
        className="fixed bottom-20 lg:bottom-6 right-4 lg:right-6 w-14 h-14 rounded-full bg-gradient-to-br from-[#2563EB] to-[#14B8A6] text-white shadow-lg shadow-blue-300/50 flex items-center justify-center hover:scale-110 transition-transform z-30"
        aria-label="Sprachassistent"
        title="Sprachassistent"
      >
        <MicIcon size={22} />
      </button>
    </div>
  );
}
