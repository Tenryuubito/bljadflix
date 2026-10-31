# 🚀 Amazon Fire TV Stick Development Guidelines & Coding Standards (2024+)

### **Zweck & Zielsetzung**
Dieses Dokument definiert die verbindlichen Architektur-Richtlinien, UX/UI-Regeln, Performance-Standards und Amazon-spezifischen Anforderungen für die Entwicklung von Smart-TV-Anwendungen auf Amazon Fire OS (Fire TV Stick, Fire TV Cube, Smart TVs).

---

## 1. CORE SKILLS & TECH STACK

### 1.1 Programmiersprache
- **Verwende ausschließlich Kotlin** (Version 1.9+ / 2.0+).
- Aktiviere die **Explicit API Mode** Policy für Multi-Module-Projekte, um klare Modulgrenzen zu erzwingen.
- **Verbot von Java-Code** in allen neuen Modulen.

### 1.2 UI-Framework: Android XML Views
- **Standard in diesem Projekt:** Klassische Android Views (AppCompat, RecyclerView, ViewBinding) mit XML-Layouts.
- Jetpack Compose for TV wird nur eingeführt, wenn eine Migration ausdrücklich gewünscht ist.

### 1.3 Architektur
- **MVVM + Unidirectional Data Flow**
  - **UI-Layer:** Activities rendern einen einzigen `UiState` aus dem ViewModel und reagieren auf Einmal-Events.
  - **Data-Layer:** `MovieRepository` (Retrofit + Gson) liefert `Result<T>`. Es gibt keinen lokalen Datenbank-Cache.
  - **DI:** Kein Framework. Jedes ViewModel hat eine verschachtelte `Factory` mit echten Default-Abhängigkeiten, Tests übergeben Mocks direkt.

### 1.4 Asynchrone Programmierung
- **Kotlin Coroutines & Flow** (`StateFlow` für Zustand, `SharedFlow` für Events).
- `CoroutineDispatcher` wird dort injiziert, wo I/O stattfindet (z. B. `MovieRepositoryImpl`), damit Tests ihn ersetzen können.

### 1.5 Media Playback
- Wiedergabe über eine **WebView** (`PlayerActivity`), die die aufgelöste Hoster-URL lädt und `<video>` bzw. jwplayer per injiziertem JavaScript steuert.
- Fortschritt wird periodisch an das Backend gemeldet, ab 90 % gilt eine Folge als gesehen.

---

## 2. FIRE TV & 10-FOOT-UI SPECIFIC RULES (UX/UI Best Practices)

### 2.1 Focus Management & D-Pad Navigation
- **Regel 1 (Keine Touch-Annahmen):** Es gibt keinen Touchscreen! Jedes interaktive Element **MUSS** fokussierbar sein (`android:focusable="true"` bzw. `isFocusable = true`).
- **Regel 2 (Explizite Focus-Pfade):** Bei komplexen Verschachtelungen (z. B. Sidebar neben einem Grid) nutze `setOnKeyListener`, `nextFocus*`-Attribute und gezieltes `requestFocus()`, um unvorhersehbares D-Pad-Springen zu verhindern.
- **Regel 3 (Focus Restoring):** Wenn der Benutzer von einem Detail-Screen oder Suchmenü zurücknavigiert, **MUSS** der vorherige Fokus-Zustand exakt wiederhergestellt werden (z. B. über die gespeicherte Auswahl im ViewModel-State).
- **Regel 4 (D-Pad-Eingaben abfangen):** Verarbeite KeyEvents für D-Pad-Tasten (`KEYCODE_DPAD_UP`, `DOWN`, `LEFT`, `RIGHT`, `CENTER`) präzise. Die `KEYCODE_BACK`-Taste **MUSS** Dialoge, Overlays oder Player-Controls konsistent schließen, bevor sie die App verlässt.

### 2.2 Visual Feedback (Fokus-Indikation)
- **Regel 1 (Deutlicher Fokus-Effekt):** Ein fokussiertes Element **MUSS** sofort ins Auge springen. Empfohlener Standard:
  - **Skalierung:** 1.05x bis 1.1x Scale-Up.
  - **Rahmen / Border:** Leuchtender Accent-Border (z. B. 2dp-3dp Solid Accent Color).
  - **Schatten / Elevation:** Animierter Schatten für Tiefenwirkung.
- **Regel 2 (Unterscheidung Selected vs. Focused):** Der Zustand "Ausgewählt" (z. B. aktiv gewählter Navigations-Tab) unterscheidet sich visuell klar vom Zustand "Fokussiert" (wo sich der D-Pad-Zeiger aktuell befindet).

### 2.3 10-Foot-Experience (Typografie & Farben)
- **Regel 1 (Schriftgrößen aus 3m Entfernung):**
  - Minimum Captions/Footnotes: `14sp - 16sp`
  - Body / Standard Text: `18sp - 20sp`
  - Subtitles / Headings: `24sp - 32sp`
  - Hero Titles / Displays: `36sp - 48sp+`
- **Regel 2 (Farben & Kontraste):**
  - Mindestkontrast von **4.5:1** (WCAG AA).
  - **VERMEIDE reines Reinweiß (`#FFFFFF`)** für große Flächen oder lange Texte, um TV-Glares und Augenermüdung zu verhindern. Nutze Off-White (z. B. `#E0E0E0`).
  - **VERMEIDE gesättigte helle Rottöne und Blautöne** an scharfen Kanten (Farbbluten/Clipping auf günstigen TV-Panels).

### 2.4 Overscan-Vermeidung (TV-Safe Zone)
- **Regel 1 (Safety Margins):** Ältere oder falsch konfigurierte TVs schneiden bis zu 5% der Bildschirmränder ab.
- **Regel 2 (Padding-Vorgabe):** Wende auf dem Top-Level Root Container aller Screens immer eine Sicherheits-Margin an:
  - **Horizontal Margin:** `48.dp` - `56.dp`
  - **Vertical Margin:** `27.dp` - `36.dp`

---

## 3. FIRE OS & AMAZON STANDARDS (System & Store Rules)

### 3.1 Umgang mit dem Fehlen von Google Play Services (GMS)
- **STRIKTE REGEL:** Verwende **KEINE** `com.google.android.gms`-Abhängigkeiten im Production Build!
- **Push Notifications:** Ersetze FCM durch **ADM (Amazon Device Messaging)** oder erstelle eine Abstraktionsschicht (`NotificationService`), die für Amazon-Builds ADM und für Standard-Android FCM nutzt.
- **In-App Purchasing:** Verwende das **Amazon Appstore In-App Purchasing (IAP) v3 SDK** anstelle von Google Play Billing.
- **Location Services:** Entferne jegliche Google Location/Maps SDKs.

### 3.2 Manifest-Konfiguration
Entferne Touch- und Telefonie-Anforderungen und deklariere die TV-Hardware im `AndroidManifest.xml`:

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.telephony" android:required="false" />
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.type.television" android:required="true" />

<application
    android:banner="@mipmap/ic_banner"
    android:icon="@mipmap/ic_launcher"
    android:logo="@mipmap/ic_banner"
    android:isGame="false">

    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:screenOrientation="landscape">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

### 3.3 Sprachsteuerung & Mediensteuerung (Alexa Integration)
- **MediaSession Compliance:** Binde eine aktive `MediaSessionCompat` ein. Alexa-Befehle ("Alexa, Pause", "Alexa, Spule 30 Sekunden vor") greifen direkt auf den `PlaybackStateCompat` der `MediaSession` zu.
- **Hardware Remote Buttons:** Registriere Key-Listener für `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_REWIND`.

---

## 4. PERFORMANCE & RESOURCE MANAGEMENT RULES

Fire TV Sticks (besonders Lite und Standard-Varianten) verfügen über stark begrenzte Hardware-Ressourcen (oft nur 1 GB – 1.5 GB RAM und schwache Quad-Core ARM-CPUs).

### 4.1 Memory Management & Player Lifecycle
- **Regel 1 (Player freigeben):** Beim Verlassen des Players die WebView stoppen und zerstören (`stopLoading()`, `destroy()`), damit Hardware-Video-Decoder und Speicher freigegeben werden.
- **Regel 2 (Polling beenden):** Laufende Fortschritts-Polls und Handler-Callbacks beim Verlassen des Screens abbrechen.

### 4.2 Bild-Laden und Caching (Glide)
- **Regel 1 (Speicher sparen):** Poster und Thumbnails mit `DecodeFormat.PREFER_RGB_565` laden (50 % weniger Arbeitsspeicher als ARGB_8888):
  ```kotlin
  Glide.with(context)
      .load(url)
      .apply(
          RequestOptions()
              .format(DecodeFormat.PREFER_RGB_565)
              .diskCacheStrategy(DiskCacheStrategy.ALL)
      )
      .into(imageView)
  ```
- **Regel 2 (Downscaling):** Lade Grafiken **NIEMALS** in Originalauflösung (z. B. 4K-Backdrops), sondern skaliere sie auf die exakt benötigten Pixel-Dimensionen.

### 4.3 Startzeit-Optimierung (Cold Start)
- **Regel 1 (Main Thread entlasten):** Keine synchronen I/O-Operations, Datenbankzugriffe oder schweren SDK-Initialisierungen auf dem Main-Thread.
- **Regel 2 (Baseline Profiles):** Generiere ein Baseline Profile (`androidx.profileinstaller`), um die JIT-Kompilierung beim App-Start und beim Scrollen durch lange Listen auf schwachen Fire TV CPUs zu minimieren.

### 4.4 Lifecycle & Power Management (Bildschirmschoner)
- **Screen Saver & Dimming:** Aktiviere `FLAG_KEEP_SCREEN_ON` **AUSSCHLIESSLICH** während der aktiven Videowiedergabe. Sobald das Video pausiert ist oder im Hauptmenü navigiert wird, MUSS die Flagge entfernt werden, damit der Fire OS-Bildschirmschoner anspringen kann.

---

## 5. CODING STANDARDS & QUALITY ASSURANCE RULES

### 5.1 Package-Struktur ("Package by Feature")
Neuer Code wird nach Features organisiert, nicht nach technischen Layer-Ordnern auf Top-Ebene:

```text
com.shadow.firetv/
├── core/
│   ├── auth/             # AuthManager (Session, Device-ID)
│   ├── data/             # MovieRepository + Implementierung
│   ├── model/            # DTOs (snake_case passend zum Backend-JSON)
│   ├── network/          # Retrofit-Client, MovieApi, AuthInterceptor
│   ├── source/           # SourceManager (aktive Streaming-Quelle)
│   └── theme/            # ThemeManager
├── feature/
│   ├── auth/             # Login
│   ├── details/          # Serien-Detailseite
│   ├── main/             # Startseite (Sidebar, Grid, Suche)
│   ├── player/           # WebView-Player
│   └── settings/         # Einstellungen
└── MainActivity.kt
```

### 5.2 Fehlerbehandlung & Offline-Feedback
- **Regel 1 (Keine "Silent Failures"):** Jeder Fehler muss abgefangen und dem Nutzer verständlich vermittelt werden.
- **Regel 2 (User-Friendly Error UI):** TV-Nutzer können keine URLs eintragen oder komplexe Formulare ausfüllen. Biete bei Netzwerkfehlern immer einen vorausgewählten D-Pad-Button an: `"Erneut versuchen"`.

### 5.3 Testing-Strategie
- **Unit Tests:** Teste ViewModels und Repositories mit JUnit 4, MockK und `Turbine` (für Flow-Testing). Der Main-Dispatcher wird über `MainDispatcherRule` ersetzt.
- **D-Pad-Navigation:** Fokuspfade bei Änderungen an Adaptern oder Layouts manuell auf dem Gerät bzw. Emulator prüfen.
