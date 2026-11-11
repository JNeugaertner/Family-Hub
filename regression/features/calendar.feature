# language: de
@regression @calendar
Funktionalität: Familienkalender
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  @smoke
  Szenario: Termin über das Formular anlegen und dauerhaft speichern
    Wenn ich den Bereich "Kalender" öffne
    Und ich über das Formular einen Testtermin für heute von "10:00" bis "11:30" anlege
    Dann sehe ich den Testtermin im Kalender
    Und liefert die API den Testtermin für heute von "10:00" bis "11:30"
    Wenn ich die Seite neu lade
    Und ich den Bereich "Kalender" öffne
    Dann sehe ich den Testtermin im Kalender

  Szenario: Ganztägigen Termin über mehrere Tage anlegen
    Wenn ich den Bereich "Kalender" öffne
    Und ich über das Formular einen ganztägigen Testtermin über 3 Tage ab heute anlege
    Dann sehe ich den Testtermin im Kalender
    Und speichert die API den Testtermin von 00:00 des ersten Tages bis 00:00 nach dem letzten Tag

  Szenario: Vorlage speichern und beim nächsten Termin verwenden
    Wenn ich den Bereich "Kalender" öffne
    Und ich im Formular einen Testtermin am Ort "Praxis Dr. Test" von "08:15" bis "08:45" ausfülle
    Und ich den Testtermin als Vorlage speichere
    Dann bestätigt das Formular die gespeicherte Vorlage
    Wenn ich das Formular schließe und einen neuen Termin mit der Vorlage beginne
    Dann übernimmt das Formular Titel, Uhrzeit "08:15" bis "08:45" und Ort "Praxis Dr. Test"

  Szenario: Ende vor Beginn wird abgewiesen
    Wenn ich per API einen Testtermin anlege, der vor seinem Beginn endet
    Dann hat die API-Antwort den Status 400

  @series
  Szenario: Wöchentliche Serie anlegen und ganz löschen
    Wenn ich per API eine wöchentliche Testserie mit 3 Terminen anlege
    Dann gibt es 3 Termine der Testserie im Abstand von 7 Tagen
    Wenn ich die Testserie ab dem ersten Termin lösche
    Dann gibt es keinen Termin der Testserie mehr

  @series
  Szenario: Einen Serientermin einzeln ändern
    Wenn ich per API eine wöchentliche Testserie mit 3 Terminen anlege
    Und ich nur den zweiten Termin der Testserie umbenenne
    Dann heißt nur der zweite Termin anders und ist als einzeln geändert markiert

  @series
  Szenario: Diesen und alle folgenden Serientermine löschen
    Wenn ich per API eine wöchentliche Testserie mit 3 Terminen anlege
    Und ich die Testserie ab dem zweiten Termin lösche
    Dann gibt es nur noch den ersten Termin der Testserie

  @permissions @security
  Szenario: Jugendliche legen eigene Termine an, gemeinsame werden Vorschläge
    Angenommen ein Testmitglied mit Rolle "jugendlicher" existiert
    Wenn das Testmitglied einen Termin nur für sich anlegt
    Dann ist der Termin des Testmitglieds "approved"
    Wenn das Testmitglied einen Termin mit dem Administrator anlegt
    Dann ist der Termin des Testmitglieds "proposed"
    Und sieht der Administrator den Vorschlag und kann ihn freigeben

  @permissions @security
  Szenario: Kinder dürfen keine Termine anlegen
    Angenommen ein Testmitglied mit Rolle "kind" existiert
    Wenn das Testmitglied einen Termin nur für sich anlegt
    Dann wird der Termin des Testmitglieds mit Status 403 abgewiesen

  @permissions @security
  Szenariogrundriss: Private Termine bleiben vor Unbeteiligten verborgen
    Angenommen ein Testmitglied mit Rolle "<Rolle>" existiert
    Wenn ich einen privaten Testtermin nur für mich anlege
    Dann sieht das Testmitglied den privaten Testtermin weder in der Liste noch direkt
    Beispiele:
      | Rolle        |
      | jugendlicher |
      | kind         |
      | gast         |

  @permissions @security @templates
  Szenario: Private Vorlagen sieht und ändert nur, wer sie angelegt hat
    Angenommen ein Testmitglied mit Rolle "jugendlicher" existiert
    Wenn ich per API eine private Testvorlage anlege
    Dann fehlt die private Testvorlage in der Vorlagenliste des Testmitglieds
    Und kann das Testmitglied die private Testvorlage weder ändern noch löschen

  @permissions @security @templates
  Szenario: Ohne Recht zum Anlegen gibt es keine Vorlagen
    Angenommen ein Testmitglied mit Rolle "kind" existiert
    Dann verweigert die API dem Testmitglied die Vorlagen mit Status 403
