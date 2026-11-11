# language: de
@regression @tasks
Funktionalität: Aufgaben
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  @smoke
  Szenario: Aufgabe über das Formular anlegen, dauerhaft speichern und abhaken
    Wenn ich den Bereich "Aufgaben" öffne
    Und ich über das Formular eine Testaufgabe anlege
    Dann sehe ich die Testaufgabe
    Wenn ich die Seite neu lade
    Und ich den Bereich "Aufgaben" öffne
    Dann sehe ich die Testaufgabe
    Wenn ich die Testaufgabe als erledigt markiere
    Dann hat die Testaufgabe laut API den Status "done"

  @repeat
  Szenario: Wiederkehrende Aufgabe mit fester Person
    Wenn ich mir per API eine Testaufgabe gebe, die sich 1 Woche nach Erledigung wiederholt
    Und ich die Testaufgabe per API abhake
    Dann gibt es eine neue Runde für mich, fällig in 7 Tagen, mit Verweis auf die erledigte
    Wenn ich den Haken der Testaufgabe zurücknehme
    Dann gibt es keine neue Runde mehr

  @repeat @permissions
  Szenario: Offene Runde übernimmt, wer sie zuerst abhakt
    Angenommen ein Testmitglied mit Rolle "jugendlicher" existiert
    Wenn ich per API eine Testaufgabe für alle anlege, die sich 3 Tage nach Erledigung wiederholt
    Und das Testmitglied die offene Runde abhakt
    Dann gehört die erledigte Runde dem Testmitglied
    Und die nächste Runde ist wieder für alle offen und in 3 Tagen fällig

  @points @permissions @security
  Szenario: Punkte gibt es erst nach Bestätigung durch einen Administrator
    Angenommen ein Testmitglied mit Rolle "kind" existiert
    Wenn ich dem Testmitglied eine Testaufgabe mit 5 Punkten gebe
    Und das Testmitglied seine Testaufgabe abhakt
    Dann wartet die Testaufgabe auf Bestätigung und das Testmitglied hat 0 Punkte
    Und das Testmitglied darf seine Testaufgabe nicht selbst bestätigen
    Wenn ich die Testaufgabe bestätige
    Dann sind dem Testmitglied 5 Punkte für die Testaufgabe gutgeschrieben
    Und zusätzlich 10 Punkte für den Erfolg "Erste Aufgabe", zusammen 15 Punkte

  @permissions @security
  Szenariogrundriss: Wer Aufgaben anlegen darf
    Angenommen ein Testmitglied mit Rolle "<Rolle>" existiert
    Wenn das Testmitglied eine Aufgabe für sich anlegt
    Dann antwortet die API mit Status <für sich>
    Wenn das Testmitglied eine Aufgabe für den Administrator anlegt
    Dann antwortet die API mit Status <für andere>
    Beispiele:
      | Rolle        | für sich | für andere |
      | jugendlicher | 201      | 403        |
      | kind         | 403      | 403        |
      | gast         | 403      | 403        |

  @bonus @permissions
  Szenario: Bonus-Aufgabe bekommt, wer zuerst kommt
    Angenommen zwei Testkinder existieren
    Wenn ich eine Bonus-Testaufgabe mit 3 Punkten anlege
    Und beide Kinder die Bonus-Testaufgabe gleichzeitig übernehmen wollen
    Dann bekommt sie genau eines der beiden
    Und das andere Kind sieht sie danach nicht mehr
