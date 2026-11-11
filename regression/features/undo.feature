# language: de
@regression @undo
Funktionalität: Löschen rückgängig machen
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  @smoke
  Szenario: Gelöschten Einkaufsartikel zurückholen
    Wenn ich den Bereich "Einkauf" öffne
    Und ich einen neuen Testartikel mit Menge "3 Stück" anlege
    Und ich den Testartikel entferne
    Dann ist der Testartikel nicht mehr vorhanden
    Und der Hinweis bietet "Rückgängig" an
    Wenn ich im Hinweis auf "Rückgängig" klicke
    Dann meldet der Hinweis "Wiederhergestellt."
    Und sehe ich den Testartikel mit Menge "3 Stück"

  Szenario: Gelöschten Termin zurückholen
    Wenn ich den Bereich "Kalender" öffne
    Und ich über das Formular einen Testtermin für heute von "14:00" bis "15:00" anlege
    Und ich den Testtermin im Kalender öffne und lösche
    Dann ist der Testtermin laut API gelöscht
    Wenn ich im Hinweis auf "Rückgängig" klicke
    Dann meldet der Hinweis "Wiederhergestellt."
    Und sehe ich den Testtermin im Kalender
    Und liefert die API den Testtermin für heute von "14:00" bis "15:00"

  Szenario: Gelöschte Aufgabe zurückholen
    Wenn ich den Bereich "Aufgaben" öffne
    Und ich über das Formular eine Testaufgabe anlege
    Und ich die Testaufgabe über das Formular lösche
    Dann ist die Testaufgabe laut API gelöscht
    Wenn ich im Hinweis auf "Rückgängig" klicke
    Dann meldet der Hinweis "Wiederhergestellt."
    Und sehe ich die Testaufgabe

  @security
  Szenario: Nur wer gelöscht hat, kann es rückgängig machen
    Angenommen ein Testmitglied mit Rolle "jugendlicher" existiert
    Wenn ich per API einen Testartikel lösche und mir die Marke merke
    Dann kann das Testmitglied die Löschung nicht rückgängig machen
    Und ich kann sie genau einmal rückgängig machen
