# language: de
@regression @shopping
Funktionalität: Gemeinsame Einkaufsliste
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet
    Und ich den Bereich "Einkauf" öffne

  @smoke
  Szenario: Artikel mit Menge anlegen und dauerhaft speichern
    Wenn ich einen neuen Testartikel mit Menge "2 Packungen" anlege
    Dann sehe ich den Testartikel mit Menge "2 Packungen"
    Wenn ich die Seite neu lade
    Und ich den Bereich "Einkauf" öffne
    Dann sehe ich den Testartikel mit Menge "2 Packungen"

  Szenario: Artikel als gekauft markieren und wieder öffnen
    Wenn ich einen neuen Testartikel mit Menge "1" anlege
    Und ich den Testartikel als gekauft markiere
    Dann ist der Testartikel gekauft
    Wenn ich den Testartikel wieder öffne
    Dann ist der Testartikel offen

  Szenario: Artikel bearbeiten
    Wenn ich einen neuen Testartikel mit Menge "1" anlege
    Und ich die Menge des Testartikels auf "3 Liter" ändere
    Dann sehe ich den Testartikel mit Menge "3 Liter"

  Szenario: Artikel löschen
    Wenn ich einen neuen Testartikel mit Menge "1" anlege
    Und ich den Testartikel entferne
    Dann ist der Testartikel nicht mehr vorhanden

  Szenario: Leere Artikel können nicht hinzugefügt werden
    Dann kann ich keinen leeren Artikel hinzufügen

  Szenario: Änderungen erscheinen in einem zweiten Browser ohne Neuladen
    Angenommen ein zweiter Administrator betrachtet die Einkaufsliste
    Wenn ich einen neuen Testartikel mit Menge "Live" anlege
    Dann erscheint der Testartikel im zweiten Browser
