# language: de
@regression @extended @backend @unicode @boundaries
Funktionalität: Backend-Eingaben mit Unicode, Sonderzeichen und Leerraum
  # Fälle und exakte Unicode-Zeichen stehen in data/input-cases.json.
  # Längengrenzen folgen dem bestehenden Java-Vertrag (UTF-16-Einheiten).
  Hintergrund:
    Angenommen ich bin für Backendtests über die API als Administrator angemeldet

  Szenariogrundriss: Neue Eingaben werden validiert und exakt gespeichert
    Wenn ich die Eingabevariante "<Variante>" mit "POST" an das Backend sende
    Dann prüfe ich Validierung und Speicherung dieser Eingabevariante
    Beispiele:
      | Variante |
      | artikel-leer |
      | artikel-leerzeichen |
      | artikel-tab |
      | artikel-zeilenumbruch |
      | artikel-em-space |
      | artikel-umlaute |
      | artikel-satzzeichen |
      | artikel-smiley |
      | artikel-nfc |
      | artikel-nfd |
      | artikel-nbspace |
      | artikel-zero-width |
      | artikel-randspaces |
      | artikel-randtabs |
      | artikel-innenspaces |
      | artikel-rand-em-space |
      | artikel-emoji-80 |
      | artikel-emoji-82 |
      | artikel-mix-81 |
      | artikel-nfd-80 |
      | artikel-nfd-82 |
      | artikel-zwj-80 |
      | artikel-zwj-88 |
      | artikel-vor-strip-80 |
      | artikel-vor-strip-82 |
      | menge-null |
      | menge-leer |
      | menge-spaces |
      | menge-tabs |
      | menge-emspace |
      | menge-rand |
      | menge-smiley |
      | menge-nbsp |
      | menge-emoji-30 |
      | menge-emoji-32 |
      | mitglied-spaces |
      | mitglied-tabs |
      | mitglied-emspace |
      | mitglied-umlaut |
      | mitglied-smiley |
      | mitglied-randspaces |
      | mitglied-nbspace |
      | mitglied-nfd |
      | mitglied-emoji-50 |
      | mitglied-emoji-52 |
      | benutzer-zeichen |
      | benutzer-randspaces |
      | benutzer-innenraum |
      | benutzer-tab |
      | benutzer-umlaut |
      | benutzer-emoji |
      | benutzer-fullwidth |
      | benutzer-nfd |
      | benutzer-nbspace |
      | passwort-sonderzeichen |
      | passwort-randspaces |
      | passwort-innenraum |
      | passwort-nurspaces |
      | passwort-umlaut-72bytes |
      | passwort-umlaut-74bytes |
      | passwort-emoji-72bytes |
      | passwort-emoji-76bytes |
      | passwort-nfd-72bytes |
      | passwort-nfd-75bytes |
      | passwort-emoji-6utf16 |
      | passwort-emoji-8utf16 |

  Szenariogrundriss: Dieselben Grenzen gelten beim Bearbeiten
    Wenn ich die Eingabevariante "<Variante>" mit "PUT" an das Backend sende
    Dann prüfe ich Validierung und Speicherung dieser Eingabevariante
    Beispiele:
      | Variante |
      | artikel-leer |
      | artikel-leerzeichen |
      | artikel-tab |
      | artikel-zeilenumbruch |
      | artikel-em-space |
      | artikel-smiley |
      | artikel-nfd |
      | artikel-randspaces |
      | artikel-randtabs |
      | artikel-innenspaces |
      | artikel-emoji-80 |
      | artikel-emoji-82 |
      | artikel-mix-81 |
      | artikel-nfd-80 |
      | artikel-nfd-82 |
      | artikel-zwj-80 |
      | artikel-zwj-88 |
      | artikel-vor-strip-80 |
      | artikel-vor-strip-82 |
      | menge-spaces |
      | menge-rand |
      | menge-emoji-30 |
      | menge-emoji-32 |
      | mitglied-spaces |
      | mitglied-smiley |
      | mitglied-randspaces |
      | mitglied-emoji-50 |
      | mitglied-emoji-52 |
      | benutzer-zeichen |
      | benutzer-randspaces |
      | benutzer-emoji |
      | passwort-randspaces |
      | passwort-umlaut-74bytes |
      | passwort-emoji-76bytes |
