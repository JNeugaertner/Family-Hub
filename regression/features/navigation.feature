# language: de
@regression @navigation
Funktionalität: Navigation der Familienanwendung
  Szenariogrundriss: Fachbereiche sind erreichbar
    Angenommen ich bin als Administrator angemeldet
    Wenn ich den Bereich "<Bereich>" öffne
    Dann sehe ich die Seite "<Titel>"
    Und der Bereich "<Bereich>" ist als aktiv markiert
    Beispiele:
      | Bereich     | Titel             |
      | Kalender    | Familienkalender  |
      | Aufgaben    | Aufgaben          |
      | Belohnungen | Belohnungen       |
      | Einkauf     | Einkaufsliste     |
      | Essensplan  | Essensplanung     |
      | Nachrichten | Nachrichten       |
      | Familie     | Familie & Profile |

  Szenario: Mobile Navigation lässt sich bedienen
    Angenommen ich bin als Administrator angemeldet
    Wenn ich die mobile Ansicht verwende
    Und ich das mobile Menü öffne
    Und ich den Bereich "Einkauf" öffne
    Dann sehe ich die Seite "Einkaufsliste"
