# language: de
@regression @auth
Funktionalität: Anmeldung und Sitzung
  @smoke
  Szenario: Erfolgreiche Anmeldung
    Angenommen ich bin als Administrator angemeldet
    Dann sehe ich die Seite "Übersicht"

  Szenario: Anmeldung bleibt nach Neuladen erhalten
    Angenommen ich bin als Administrator angemeldet
    Wenn ich die Seite neu lade
    Dann sehe ich die Seite "Übersicht"

  Szenario: Abmeldung beendet die Sitzung
    Angenommen ich bin als Administrator angemeldet
    Wenn ich mich abmelde
    Dann sehe ich das Anmeldeformular
    Und die API verweigert den Zugriff auf mein Profil

  Szenario: Falsches Passwort wird abgelehnt
    Angenommen ich öffne die Anmeldung
    Wenn ich mich mit einem unbekannten Benutzer anmelde
    Dann sehe ich eine Anmeldefehlermeldung
    Und die API verweigert den Zugriff auf mein Profil

  Szenario: Leere Zugangsdaten können nicht abgesendet werden
    Angenommen ich öffne die Anmeldung
    Dann ist die Anmeldung ohne Zugangsdaten deaktiviert

  Szenario: Sitzungen verschiedener Browser sind getrennt
    Angenommen ich bin als Administrator angemeldet
    Dann ist ein zweiter Browser nicht angemeldet
