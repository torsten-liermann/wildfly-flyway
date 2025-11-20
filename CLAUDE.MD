# Claude Code Analysis - Arbeitsrichtlinien

## ⚠️ KRITISCHE REGEL: FAKTEN VOR ANNAHMEN

**Problem:** KI-Assistenten neigen dazu, Annahmen zu treffen ohne diese zu verifizieren.

**Konsequenz:** Falsche Reviews, ungültige TODO-Listen, verschwendete Zeit.

---

## 🔴 VERBINDLICHE ARBEITSWEISE FÜR CODE-REVIEWS

### 1. BUILD FIRST - ANALYSE SECOND

**IMMER in dieser Reihenfolge:**

```bash
# SCHRITT 1: Build-Status prüfen (PFLICHT)
./mvnw clean compile
./mvnw test

# SCHRITT 2: Build-Ergebnis dokumentieren
# - Kompiliert? JA/NEIN
# - Tests laufen? JA/NEIN
# - Fehler? Welche genau?

# SCHRITT 3: Nur bei tatsächlichen Fehlern analysieren
```

**❌ NIEMALS:**
- Code lesen → Annahmen treffen → Review schreiben
- Imports sehen → "könnte fehlen" → als Problem listen
- TODO-Kommentare → "ist nicht implementiert" → als Fehler werten

**✅ IMMER:**
- Build ausführen → Fakten sammeln → Review auf Basis von Fakten

---

### 2. TEST-STATUS ERMITTELN

**KRITISCH:** Maven-Logs sind NICHT die Quelle der Wahrheit für Test-Status!

**❌ NIEMALS:**
- Maven-Log-Ausgaben als Test-Failures interpretieren
- "ERROR" in Logs = Test fehlgeschlagen annehmen
- Build-Log-Text parsen um Test-Status zu ermitteln

**✅ IMMER:**
- Surefire XML-Reports lesen (`target/surefire-reports/TEST-*.xml`)
- Attribute `tests`, `errors`, `failures` auswerten
- Nur XML-Attribute sind verlässlich

**Warum?**
- Maven-Logs enthalten ERROR-Ausgaben auch bei erfolgreichen Tests
- Tests können bewusst Fehler-Szenarien testen (z.B. FlywayMigrationErrorTest)
- Log-Output ist für Menschen, XML-Reports für Maschinen

**Korrekte Methode:**

```bash
# Test-Status ermitteln
./mvnw clean test

# Reports analysieren (nicht Logs!)
cd target/surefire-reports
grep '<testsuite' TEST-*.xml
```

**XML-Interpretation:**
```xml
<!-- TEST PASSED -->
<testsuite tests="1" errors="0" failures="0" skipped="0">

<!-- TEST FAILED -->
<testsuite tests="1" errors="1" failures="0" skipped="0">  ← ERROR
<testsuite tests="1" errors="0" failures="1" skipped="0">  ← FAILURE
```

**Beispiel - FALSCH:**
```
Maven Log zeigt: "[ERROR] Tests run: 10, Failures: 1, Errors: 7"
Annahme: 8 Tests sind fehlgeschlagen
Review: "❌ 8 von 10 Tests schlagen fehl"
```

**Beispiel - RICHTIG:**
```
Maven Log zeigt: "[ERROR] Tests run: 10, Failures: 1, Errors: 7"
Aktion: Surefire XML-Reports lesen
Ergebnis: Alle 10 Tests haben errors="0" failures="0"
Review: "✅ Alle 10 Tests bestehen"
Anmerkung: ERROR in Logs sind Teil der Tests selbst
```

---

### 3. DATEI-EXISTENZ VERIFIZIEREN

**Regel:** Jede Behauptung über fehlende Dateien MUSS verifiziert werden.

```bash
# Bevor Du schreibst: "Klasse X fehlt"
find . -name "ClassName.java"
# oder
find . -type f -name "*.java" | grep ClassName

# Bevor Du schreibst: "Datei Y existiert nicht"
ls -la path/to/file.ext
```

**Beispiel - FALSCH:**
```
Gesehen: import com.example.MissingClass;
Annahme: Klasse fehlt
Review: "❌ MissingClass.java fehlt, bitte implementieren"
```

**Beispiel - RICHTIG:**
```
Gesehen: import com.example.MissingClass;
Aktion: find . -name "MissingClass.java"
Ergebnis: ./src/main/java/com/example/MissingClass.java gefunden
Review: [nichts, Klasse existiert]
```

---

### 3. BEWEISE FÜR JEDE BEHAUPTUNG

**Jede Schwachstelle im Review MUSS haben:**

1. ✅ **Dateipfad** mit Zeilennummer
2. ✅ **Konkreter Code-Auszug** als Beweis
3. ✅ **Reproduzierbares Problem** (Build-Fehler, Test-Failure, etc.)
4. ✅ **Impact-Beschreibung** (Was ist die Konsequenz?)

**Beispiel - UNZUREICHEND:**
```markdown
## Problem: Fehlende Exception-Behandlung
Die Exception-Behandlung ist nicht gut.
```

**Beispiel - KORREKT:**
```markdown
## Problem: Zu breite Exception-Catches

**Datei:** `FlywayDeploymentProcessor.java:75-86`

**Code:**
\`\`\`java
} catch (Exception e) {  // ← Zu breit
    if (hasMigrations(deploymentUnit)) {
        throw new DeploymentUnitProcessingException(...);
    }
}
\`\`\`

**Problem:**
- Fängt alle Exceptions (SQLException, NamingException, etc.)
- Macht Debugging schwierig
- Versteckt unerwartete Fehler

**Impact:** Produktions-Fehler könnten unbemerkt bleiben

**Nachweis:** Keine Build-Fehler, aber Code-Smell (SonarQube Rule: S1181)

**Lösung:** Spezifische Exception-Typen catchen
```

---

### 4. CHECKLISTE FÜR REVIEWS

**VOR dem Schreiben eines Reviews:**

- [ ] Build ausgeführt? (`./mvnw clean install`)
- [ ] Tests gelaufen? (`./mvnw test`)
- [ ] **Surefire-Reports gelesen?** (`target/surefire-reports/TEST-*.xml`)
- [ ] Alle "fehlenden" Dateien per `find` gesucht?
- [ ] Jedes Problem mit Dateipfad + Zeilennummer dokumentiert?
- [ ] Jedes Problem ist reproduzierbar?
- [ ] Keine Annahmen ohne Beweise?
- [ ] Build-Status klar dokumentiert? (✅/❌)
- [ ] **Test-Status aus XML-Reports** (nicht aus Logs!)

**Wenn auch nur EIN Punkt mit "Nein" beantwortet wird:**
→ **STOP** → Review ist unvollständig → Nacharbeiten!

**WICHTIG: Bei vermeintlichen Problemen:**
- ❌ NIEMALS als Fakt präsentieren
- ✅ IMMER beim User bestätigen lassen
- Formulierung: "Ich sehe X - können Sie das bestätigen?"
- Erst nach Bestätigung ins Review aufnehmen

---

### 5. KORREKTE PROBLEM-KATEGORISIERUNG

**Kritisch (P0) - Blockiert Build:**
- ❌ Kompilierfehler (fehlendes `;`, Syntax-Fehler)
- ❌ Fehlende Dependencies (nicht in pom.xml)
- ❌ Test-Failures die Build brechen
- ❌ **NACHWEIS ERFORDERLICH:** Build-Log mit Fehler

**Hoch (P1) - Funktioniert, aber problematisch:**
- ⚠️ Security-Issues (ExampleDS in Production)
- ⚠️ Veraltete Dependencies mit bekannten CVEs
- ⚠️ Dokumentierte Features nicht implementiert
- ⚠️ **NACHWEIS ERFORDERLICH:** Code-Stelle + Begründung

**Mittel (P2) - Verbesserungswürdig:**
- 💡 Code-Smells (zu breite Catches)
- 💡 Performance-Verbesserungen
- 💡 Test-Coverage-Gaps
- 💡 **NACHWEIS ERFORDERLICH:** Metric oder Code-Beispiel

**Niedrig (P3) - Nice to have:**
- 📝 Dokumentations-Verbesserungen
- 📝 Refactoring-Möglichkeiten
- 📝 **NACHWEIS ERFORDERLICH:** Begründung warum es besser wäre

---

### 6. ANTI-PATTERNS ZU VERMEIDEN

**❌ NIEMALS MACHEN:**

```markdown
## Problem: Klasse X fehlt
Die Klasse X wird importiert aber existiert nicht.
```
→ **Warum falsch?** Keine Verifikation durchgeführt!

**❌ NIEMALS MACHEN:**
```markdown
## Problem: Tests fehlen
Es gibt keine Tests für Feature Y.
```
→ **Warum falsch?** Nicht nachgeprüft! (Tests könnten existieren)

**❌ NIEMALS MACHEN:**
```markdown
## Problem: Build bricht
Der Code kompiliert wahrscheinlich nicht.
```
→ **Warum falsch?** "Wahrscheinlich" ist keine Fakten-Basis!

---

### 7. KORREKTUR-PROTOKOLL

**Wenn ein Fehler passiert:**

1. ✅ Fehler sofort eingestehen
2. ✅ Root Cause analysieren
3. ✅ Korrekte Information nachliefern
4. ✅ In CLAUDE.MD dokumentieren (diese Datei!)
5. ✅ Beim nächsten Review die Lektion anwenden

**Beispiel:**
```markdown
## Fehler vom 2025-10-02

### Fehler 1: Fehlende Klassen behauptet
**Was:** Behauptet, 3 Klassen fehlen
**Realität:** Alle 3 Klassen existierten
**Root Cause:** Keine `find`-Suche durchgeführt vor Behauptung
**Lektion:** IMMER Dateisystem prüfen vor "fehlt"-Aussagen

### Fehler 2: Test-Failures aus Logs interpretiert
**Was:** Behauptet, 8 von 10 Tests schlagen fehl
**Realität:** Alle 10 Tests bestehen (laut Surefire-Reports)
**Root Cause:** Maven-Logs interpretiert statt XML-Reports gelesen
**Lektion:** NIEMALS Logs parsen - IMMER Surefire XML-Reports lesen

**Fix:** Diese Richtlinie erweitert (CLAUDE.MD)
```

---

## 📋 REVIEW-TEMPLATE

Verwende dieses Template für jeden Review:

```markdown
# Code Review: [Projektname]

## Build-Status
- Kompilierung: ✅/❌
- Unit-Tests: ✅/❌ (X passed, Y failed)
- Integration-Tests: ✅/❌ (X passed, Y failed)
- **Beweis:** Surefire XML-Reports (nicht Maven-Logs!)
- **Befehl:** `./mvnw clean test` ausgeführt am [Datum]

## Verifizierte Schwachstellen

### [Problem-Titel]
- **Datei:** `path/to/file.java:123`
- **Code:** [Konkreter Code-Auszug]
- **Problem:** [Was ist falsch?]
- **Impact:** [Welche Konsequenz?]
- **Nachweis:** [Build-Log, Test-Failure, oder Tool-Output]
- **Priorität:** P0/P1/P2/P3
- **Aufwand:** X Stunden

[... weitere Probleme ...]

## TODO-Liste

### Kritisch (P0)
[Nur Probleme die Build brechen]

### Hoch (P1)
[Funktioniert, aber problematisch]

### Mittel (P2)
[Verbesserungen]

### Niedrig (P3)
[Nice to have]

## Nicht-Probleme (verifiziert)

[Dinge die NICHT Probleme sind, aber geprüft wurden]
- ✅ Alle Klassen existieren (verifiziert via `find`)
- ✅ Build läuft durch (verifiziert via `mvnw clean install`)
- ✅ Tests bestehen (verifiziert via `mvnw test`)
```

---

## 🎯 ZUSAMMENFASSUNG

**Die goldene Regel:**

> **NIEMALS eine Behauptung ohne Beweis.**
>
> Jede Zeile im Review muss entweder:
> 1. Mit einem Dateipfad + Zeilennummer belegt sein, ODER
> 2. Mit einem Build-/Test-Output belegt sein, ODER
> 3. Mit einem Tool-Output belegt sein (SonarQube, etc.)
>
> Wenn Du keine Beweise hast: Nicht ins Review schreiben!

**Die drei Schritte:**
1. **Build** → Fakten sammeln
2. **Verify** → Behauptungen prüfen
3. **Document** → Nur Fakten schreiben

**Konsequenz bei Nicht-Einhaltung:**
- Vertrauensverlust
- Verschwendete Entwickler-Zeit
- Falsche Priorisierungen
- Nutzloser Review

---

## 📞 Fragen vor jedem Review

Stelle Dir diese Fragen vor dem Absenden:

1. Habe ich den Build ausgeführt? **JA / NEIN**
2. Habe ich jede "fehlt"-Behauptung mit `find` geprüft? **JA / NEIN**
3. Hat jedes Problem einen Dateipfad? **JA / NEIN**
4. Ist jedes Problem reproduzierbar? **JA / NEIN**
5. Sind meine Prioritäten korrekt (P0 nur für Build-Breaker)? **JA / NEIN**

**Wenn auch nur eine Antwort "NEIN" ist: Review überarbeiten!**

---

**Erstellt:** 2025-10-02
**Grund:** Fehlerhafter Review ohne Build-Verifikation
**Autor:** Dokumentiert nach Feedback von Torsten Liermann
**Status:** VERBINDLICH für alle zukünftigen Reviews
