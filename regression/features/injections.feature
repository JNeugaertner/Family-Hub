# language: de
@regression @extended @injection @security
Funktionalität: Injection- und Manipulationsversuche
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  Szenariogrundriss: Gespeicherte XSS-Payloads bleiben reiner Text
    Wenn ich die XSS-Variante "<Variante>" als Artikel speichere
    Dann wird die Payload auch nach Neuladen als Text ohne Skriptausführung angezeigt
    Beispiele:
      | Variante |
      | img      |
      | script   |
      | svg      |

  Szenariogrundriss: Login-Injections umgehen die Anmeldung nicht
    Wenn eine neue Browsersitzung die Login-Injection "<Variante>" sendet
    Dann bleibt die neue Browsersitzung unangemeldet
    Beispiele:
      | Variante |
      | sql      |
      | nosql    |
      | operator |

  Szenariogrundriss: Operatorobjekte werden nicht als Kontofelder interpretiert
    Wenn ich ein Operatorobjekt im Kontofeld "<Feld>" sende
    Dann wird die Anfrage ohne internen Fehler abgewiesen
    Beispiele:
      | Feld     |
      | username |
      | password |

  Szenario: Kind kann serverseitige Artikelfelder nicht überschreiben
    Angenommen ein Testmitglied mit Rolle "kind" existiert
    Wenn das Testmitglied einen Artikel mit gefälschtem Status und Ersteller anlegt
    Dann bleibt der Artikel ein ungeprüfter Vorschlag des Testmitglieds

  Szenario: Ungültiger CSRF-Token verhindert eine Änderung
    Wenn ich mit einem gefälschten CSRF-Token einen Artikel anlege
    Dann hat die API-Antwort den Status 403

  Szenario: MongoDB-Operator als Artikeltext verändert keine Abfrage
    Wenn ich einen MongoDB-Operator als reinen Artikeltext speichere
    Dann bleibt der Operator reiner Text und bestehende Artikel bleiben unverändert
