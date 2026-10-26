# language: de
@regression @a11y
Funktionalität: Automatische Barrierefreiheitsprüfung
  Szenariogrundriss: WCAG-Prüfung der Hauptbereiche
    Angenommen ich bin als Administrator angemeldet
    Wenn ich den Bereich "<Bereich>" öffne
    Dann prüfe ich "<Bereich>" mit axe auf WCAG A und AA
    Beispiele:
      | Bereich     |
      | Übersicht   |
      | Kalender    |
      | Aufgaben    |
      | Belohnungen |
      | Einkauf     |
      | Essensplan  |
      | Nachrichten |
      | Familie     |

  Szenario: WCAG-Prüfung der Anmeldung
    Angenommen ich öffne die Anmeldung
    Dann prüfe ich "Anmeldung" mit axe auf WCAG A und AA

  Szenario: Mobile Navigation ist zugänglich
    Angenommen ich bin als Administrator angemeldet
    Wenn ich die mobile Ansicht verwende
    Und ich das mobile Menü öffne
    Dann prüfe ich "Mobile Navigation" mit axe auf WCAG A und AA

  Szenario: Anmeldung ist vollständig mit der Tastatur bedienbar
    Angenommen ich öffne die Anmeldung
    Dann kann ich mich ausschließlich mit der Tastatur anmelden
