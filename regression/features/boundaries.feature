# language: de
@regression @extended @boundaries
Funktionalität: Grenzwerte der Eingabevalidierung
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  Szenariogrundriss: Längengrenzen der Einkaufsliste
    Wenn ich einen Artikel mit Feld "<Feld>" und Länge <Länge> per API anlege
    Dann ist der Validierungsstatus <Status> für Feld "<Feld>"
    Beispiele:
      | Feld     | Länge | Status |
      | name     | 0     | 400    |
      | name     | 1     | 201    |
      | name     | 80    | 201    |
      | name     | 81    | 400    |
      | quantity | 0     | 201    |
      | quantity | 30    | 201    |
      | quantity | 31    | 400    |

  Szenariogrundriss: Längengrenzen von Benutzerkonten
    Wenn ich ein Mitglied mit Feld "<Feld>" und Länge <Länge> per API anlege
    Dann ist der Validierungsstatus <Status> für Feld "<Feld>"
    Beispiele:
      | Feld     | Länge | Status |
      | name     | 0     | 400    |
      | name     | 1     | 201    |
      | name     | 50    | 201    |
      | name     | 51    | 400    |
      | username | 2     | 400    |
      | username | 3     | 201    |
      | username | 30    | 201    |
      | username | 31    | 400    |
      | password | 7     | 400    |
      | password | 8     | 201    |
      | password | 72    | 201    |
      | password | 73    | 201    |
      | password | 100   | 201    |
      | password | 101   | 400    |

  Szenariogrundriss: Ungültige Kontofelder
    Wenn ich ein Mitglied mit ungültigem Feld "<Feld>" und Wert "<Wert>" anlege
    Dann ist der Validierungsstatus 400 für Feld "<Feld>"
    Beispiele:
      | Feld      | Wert       |
      | username  | UpperCase  |
      | username  | mit leer   |
      | color     | red        |
      | color     | #GG0000    |
      | birthDate | 2999-01-01 |
      | role      | ki_agent   |

  Szenario: Höchstens zwei Administratoren
    Dann akzeptiert die API genau einen weiteren Administrator und verweigert den dritten

  Szenario: Höchstens fünf Kinder einschließlich Jugendlicher
    Dann akzeptiert die API fünf Kinder und verweigert das sechste

  Szenariogrundriss: Altersgrenze für Jugendliche
    Wenn ich ein Kind im Alter von <Alter> Jahren mit Rollenfixierung "<Fixiert>" anlege
    Dann hat das Mitglied die effektive Rolle "<Rolle>"
    Beispiele:
      | Alter | Fixiert | Rolle        |
      | 12    | nein    | kind         |
      | 13    | nein    | jugendlicher |
      | 13    | ja      | kind         |
