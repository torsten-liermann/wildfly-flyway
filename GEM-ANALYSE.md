# Analyse für den Umbau zu einer Cloud-Native Konfiguration (Version 4 - Final)

Dieses Dokument beschreibt die notwendigen Änderungen, um das `wildfly-flyway`-Subsystem auf eine flexible, sichere, kompatible und Cloud-native Konfigurationshierarchie umzustellen.

**Ziele:**

1.  **Klare Konfigurationshierarchie:** Eine mehrstufige, präzise definierte Konfigurationslogik.
2.  **Maximale Kompatibilität:** Unterstützung für `flyway.*`- und `spring.flyway.*`-Properties.
3.  **Sicherheit als Standard:** Riskante Features wie die automatische Erkennung von Datenquellen sind standardmäßig deaktiviert (Opt-in).
4.  **Vollständige Konfigurierbarkeit:** Alle wichtigen Flyway-Optionen sind über das Subsystem und Umgebungsvariablen steuerbar.

---

## 1. Definiertes Konfigurationsmodell

Die Konfiguration wird nach der folgenden, absteigenden Prioritätshierarchie ermittelt.

### Stufe 1: `flyway.properties` im Deployment (Höchste Priorität)

-   **Zweck:** Vollständige, anwendungsspezifische Konfiguration.
-   **Quelle:** Eine `flyway.properties`-Datei im Classpath des Deployments.
-   **Funktionalität:**
    -   **Namensraum-Unterstützung:** Es werden Properties aus beiden Namensräumen gelesen. Bei Konflikten hat der `flyway.*`-Namensraum Vorrang (z.B. `flyway.datasource` gewinnt gegen `spring.flyway.datasource`).
    -   **Placeholder-Unterstützung:** Alle Property-Werte werden durch den `PropertyReplacer` des Deployments aufgelöst, was die Nutzung von `${env.VAR}` etc. ermöglicht.
    -   **Sicherheit (Opt-in):** Die automatische Datenquellen-Erkennung (Stufe 3) wird nur dann aktiviert, wenn in dieser Datei die Eigenschaft `flyway.auto-discovery.enabled=true` gesetzt ist.
    -   **Vollständige Überschreibung:** Wenn diese Datei vorhanden ist, werden alle globalen Flyway-Einstellungen aus dem Subsystem für dieses Deployment **ignoriert**.

### Stufe 2: Subsystem-Konfiguration in `standalone.xml` (Globale Konfiguration)

-   **Zweck:** Globale Standardkonfiguration für alle Deployments.
-   **Quelle:** Das `<subsystem>`-Element in `standalone.xml`.
-   **Funktionalität:**
    -   Die Konfiguration wird um wichtige Flyway-Attribute erweitert.
    -   Alle Attributwerte unterstützen Expressions und werden standardmäßig über Umgebungsvariablen gesteuert.
    -   **Beispielkonfiguration im Feature-Pack:**
        ```xml
        <subsystem xmlns="urn:com.github.wildfly.flyway:1.0"
                   enabled="${env.FLYWAY_ENABLED:true}"
                   default-datasource="${env.FLYWAY_DATASOURCE:}"
                   baseline-on-migrate="${env.FLYWAY_BASELINE_ON_MIGRATE:false}"
                   clean-disabled="${env.FLYWAY_CLEAN_DISABLED:true}"
                   validate-on-migrate="${env.FLYWAY_VALIDATE_ON_MIGRATE:true}"
                   locations="${env.FLYWAY_LOCATIONS:classpath:db/migration}"
                   table="${env.FLYWAY_TABLE:flyway_schema_history}"/>
        ```

### Stufe 3: Automatische JNDI-Erkennung (Gesicherter Fallback)

-   **Zweck:** Automatische Konfiguration in einfachen Umgebungen.
-   **Bedingungen (alle müssen erfüllt sein):**
    1.  Es wurde weder in Stufe 1 noch in Stufe 2 eine Datenquelle konfiguriert.
    2.  Die Eigenschaft `flyway.auto-discovery.enabled` wurde in den anwendungsspezifischen Properties (Stufe 1) explizit auf `true` gesetzt.
-   **Funktionalität:**
    -   Das Subsystem durchsucht `java:jboss/datasources/` nach Instanzen von `javax.sql.DataSource`.
    -   **Erfolgsfall:** Bei **genau einer** gefundenen Datenquelle wird diese verwendet.
    -   **Fehlerfall (Deployment schlägt fehl):** Bei **null** oder **mehr als einer** gefundenen Datenquelle wird eine aussagekräftige `DeploymentUnitProcessingException` geworfen.

---

## 2. Notwendige Implementierungsänderungen

### `FlywaySubsystemDefinition.java`

-   Die Definition wird um die neuen `AttributeDefinition`-Konstanten erweitert:
    -   `BASELINE_ON_MIGRATE`
    -   `CLEAN_DISABLED`
    -   `VALIDATE_ON_MIGRATE`
    -   `LOCATIONS`
    -   `TABLE`
-   Diese neuen Attribute werden in der `registerAttributes`-Methode registriert.

### `FlywaySubsystemParser.java`

-   Der Parser wird erweitert, um die neuen Attribute aus der XML-Konfiguration zu lesen und dem `subsystemAdd`-Operationsmodell hinzuzufügen.

### `FlywayDeploymentProcessor.java`

-   Die Kernlogik wird überarbeitet, um die neue Hierarchie und die Sicherheitsanforderungen abzubilden.
-   **Property-Verarbeitung:**
    1.  Laden der `flyway.properties`.
    2.  Erstellen eines einheitlichen `Properties`-Objekts, bei dem `flyway.*`-Werte Vorrang vor `spring.flyway.*`-Werten haben.
    3.  Anwenden des `PropertyReplacer` auf alle Werte.
-   **Logik-Fluss:**
    1.  Prüfen, ob `flyway.properties` vorhanden sind. Wenn ja, diese für die Konfiguration verwenden.
    2.  Wenn nein, die Konfiguration aus dem Subsystem-Modell lesen.
    3.  Prüfen, ob eine Datenquelle konfiguriert ist.
    4.  Wenn nein, prüfen, ob `flyway.auto-discovery.enabled` auf `true` gesetzt ist. Wenn ja, Stufe 3 ausführen.
    5.  Wenn keine Datenquelle gefunden/konfiguriert wurde, das Deployment fehlschlagen lassen.

### `feature-pack` Modul

-   Die `layer-spec.xml` wird angepasst, um die erweiterte Standardkonfiguration mit allen neuen Attributen und den entsprechenden `${env.*}`-Placeholdern zu installieren.

---

## 3. Notwendige Dokumentationsänderungen (`README.md`)

-   Die Dokumentation wird vollständig überarbeitet, um das finale, dreistufige Modell präzise zu beschreiben.
-   Die Unterstützung beider Property-Namensräume (`flyway.*` und `spring.flyway.*`) und deren Priorisierung wird erklärt.
-   Das Opt-in-Verhalten für die Auto-Erkennung wird als Sicherheitsfeature hervorgehoben.
-   Eine vollständige Tabelle aller konfigurierbaren Subsystem-Attribute und der zugehörigen Umgebungsvariablen wird hinzugefügt.