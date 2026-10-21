# language: de
@regression @security
Funktionalität: Zugriffsschutz der API
  Szenariogrundriss: Geschützte Daten sind anonym nicht lesbar
    Angenommen ich öffne die Anmeldung
    Dann liefert der anonyme API-Aufruf "<Pfad>" den Status 401
    Beispiele:
      | Pfad          |
      | /api/auth/me  |
      | /api/shopping |

  Szenario: Schreibzugriffe benötigen einen CSRF-Token
    Angenommen ich bin als Administrator angemeldet
    Wenn ich einen Artikel ohne CSRF-Token per API anlege
    Dann hat die API-Antwort den Status 403

  Szenario: Ersteinrichtung ist nach Einrichtung gesperrt
    Angenommen ich bin als Administrator angemeldet
    Wenn ich die Ersteinrichtung erneut per API aufrufe
    Dann hat die API-Antwort den Status 409
