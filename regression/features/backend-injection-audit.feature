# language: de
@regression @security @injectionaudit
Funktionalität: Zusätzliche Injection-Versuche direkt über HTTP
  Hintergrund:
    Angenommen ich bin für Backendtests über die API als Administrator angemeldet

  Szenariogrundriss: Backend verarbeitet Angriffseingaben sicher
    Wenn ich den Backend-Injection-Versuch "<Versuch>" ausführe
    Dann hat der Versuch die erwartete sichere Wirkung
    Beispiele:
      | Versuch              |
      | mongo-ne             |
      | mongo-regex          |
      | mongo-where          |
      | login-operator-form  |
      | login-operator-json  |
      | login-sql-comment    |
      | xss-name-svg         |
      | xss-quantity-img     |
      | xss-member-name      |
      | xss-encoded-html     |
      | command-literal      |
      | template-literal     |
      | prototype-properties |
      | username-crlf        |
      | traversal-member     |
