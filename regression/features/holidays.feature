# language: de
@regression @holidays
Funktionalität: Countdown zu Feiertagen
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  @smoke
  Szenario: Die Übersicht zählt bis zum nächsten Feiertag herunter
    Dann zeigt die Übersicht den nächsten Feiertag laut API mit den verbleibenden Tagen
    Und darunter die beiden Feiertage danach

  Szenario: Der Kalender zeigt den Countdown und markiert den Feiertag
    Wenn ich den Bereich "Kalender" öffne
    Dann zeigt der Kalender den Countdown zum nächsten Feiertag
    Wenn ich im Monatskalender zum Monat des nächsten Feiertags blättere
    Dann ist der nächste Feiertag an seinem Tag markiert
    Wenn ich den Tag des nächsten Feiertags öffne
    Dann zeigt die Tagesansicht den Feiertag

  Szenario: Ostern, Himmelfahrt und Pfingsten wandern mit dem Osterdatum
    Dann liefert die API für 2027 die Feiertage
      | key                       | date       |
      | neujahr                   | 2027-01-01 |
      | ostern                    | 2027-03-28 |
      | christi_himmelfahrt       | 2027-05-06 |
      | pfingsten                 | 2027-05-16 |
      | tag_der_deutschen_einheit | 2027-10-03 |
      | weihnachten               | 2027-12-24 |

  @security
  Szenario: Auch Gäste sehen Feiertage, ohne Anmeldung niemand
    Angenommen ein Testmitglied mit Rolle "gast" existiert
    Dann sieht das Testmitglied die nächsten Feiertage
    Und ohne Anmeldung liefert die API keine Feiertage
    Und die API lehnt einen Zeitraum über 400 Tage ab
