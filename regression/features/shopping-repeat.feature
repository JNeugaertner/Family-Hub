# language: de
@regression @shopping @repeat
Funktionalität: Wiederkehrende Artikel auf der Einkaufsliste
  Hintergrund:
    Angenommen ich bin als Administrator angemeldet

  Szenario: Artikel für jeden Einkauf steht nach dem Entfernen sofort wieder offen da
    Wenn ich per API einen wiederkehrenden Testartikel mit Rhythmus "trip" anlege
    Und ich den Testartikel per API abhake und die abgehakten Artikel entferne
    Dann steht der Testartikel wieder offen auf der Liste

  Szenario: Artikel mit Abstand kommt nach dem Kauf erst später wieder
    Wenn ich per API einen wiederkehrenden Testartikel mit Rhythmus "2 weeks" anlege
    Und ich den Testartikel per API abhake und die abgehakten Artikel entferne
    Dann kommt der Testartikel in 14 Tagen wieder

  Szenario: Gewöhnliche Artikel verschwinden beim Entfernen
    Wenn ich per API einen gewöhnlichen Testartikel anlege
    Und ich den Testartikel per API abhake und die abgehakten Artikel entferne
    Dann ist der Testartikel von der Liste verschwunden
