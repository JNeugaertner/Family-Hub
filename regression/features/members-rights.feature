# language: de
@regression @extended @members @permissions @security
Funktionalität: Benutzerverwaltung und Berechtigungen
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  Szenario: Benutzer anlegen ändern anmelden und löschen
    Angenommen ein Testmitglied mit Rolle "gast" existiert
    Dann erscheint das Testmitglied in der Familienoberfläche
    Wenn ich Namen und Passwort des Testmitglieds ändere
    Dann funktioniert das neue Passwort und das bisherige wird abgewiesen
    Wenn ich das Testmitglied lösche
    Dann ist das Konto nicht mehr lesbar und die bestehende Sitzung hat keinen Zugriff

  Szenario: Benutzername muss eindeutig sein
    Angenommen ein Testmitglied mit Rolle "gast" existiert
    Wenn ich dessen Benutzername erneut vergebe
    Dann ist der Validierungsstatus 400 für Feld "username"

  Szenariogrundriss: Nichtadministratoren dürfen keine Konten verwalten
    Angenommen ein Testmitglied mit Rolle "<Rolle>" existiert
    Dann darf das Testmitglied keine Mitglieder anlegen ändern oder löschen
    Beispiele:
      | Rolle        |
      | gast         |
      | kind         |
      | jugendlicher |

  Szenariogrundriss: Schreibrechte auf der Einkaufsliste
    Angenommen ein Testmitglied mit Rolle "<Rolle>" existiert
    Wenn das Testmitglied einen Artikel anlegt
    Dann entspricht das Ergebnis dem Einkaufsrecht "<Ergebnis>"
    Beispiele:
      | Rolle        | Ergebnis |
      | gast         | verboten |
      | kind         | proposed |
      | jugendlicher | approved |

  Szenario: Kinder sehen fremde Vorschläge nicht und können sie nicht verändern
    Angenommen zwei Testkinder existieren
    Dann bleiben Vorschläge des ersten Kindes vor dem zweiten verborgen

  Szenario: Nur Administratoren dürfen Kindervorschläge genehmigen
    Angenommen ein Testmitglied mit Rolle "kind" existiert
    Dann kann das Kind seinen Vorschlag nicht genehmigen aber der Administrator kann es

  Szenario: Gast sieht keine fremden Anmeldedaten oder Rechte
    Angenommen ein Testmitglied mit Rolle "gast" existiert
    Dann enthält die Mitgliederantwort keine fremden Zugangsdaten oder Passworthashes

  Szenario: Einzelrechte lassen sich vergeben und unmittelbar entziehen
    Angenommen ein Testmitglied mit Rolle "gast" existiert
    Dann gelten zusätzliche Einkaufsrechte und ihr Entzug auch in der bestehenden Sitzung

  Szenario: Begrenzte Familienverwaltung erlaubt keine Rechteausweitung
    Angenommen ein Testmitglied mit Rolle "gast" existiert
    Dann darf ein begrenzter Familienverwalter weder sich zum Administrator machen noch fremde Passwörter ändern

  Szenario: Letzter Administrator darf nicht gelöscht oder herabgestuft werden
    Dann bleiben Löschung und Herabstufung des einzigen Administrators gesperrt
