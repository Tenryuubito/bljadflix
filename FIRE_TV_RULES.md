# 🚀 Amazon Fire TV Stick Development Guidelines & Coding Standards (2024+)

### **Zweck & Zielsetzung**
Dieses Dokument definiert die verbindlichen Architektur-Richtlinien, UX/UI-Regeln, Performance-Standards und Amazon-spezifischen Anforderungen für die Entwicklung von Smart-TV-Anwendungen auf Amazon Fire OS (Fire TV Stick, Fire TV Cube, Smart TVs).

---

## 1. CORE SKILLS & TECH STACK

### 1.1 Programmiersprache
- **Verwende ausschließlich Kotlin** (Version 1.9+ / 2.0+).
- Aktiviere die **Explicit API Mode** Policy für Multi-Module-Projekte, um klare Modulgrenzen zu erzwingen.
- **Verbot von Java-Code** in allen neuen Modulen.

### 1.2 UI-Framework: Jetpack Compose for TV vs. Leanback
- **Standard (2024+): Jetpack Compose for TV (`androidx.tv.material3`)**
  - **Begründung:** Modernes deklaratives UI-Paradigma, nahtlose Zustandssynchronisation, flexible Animationen und deutlich höhere Entwicklungsgeschwindigkeit im Vergleich zum klassischen Leanback-XML-System.
  - **Ausnahme / Legacy-Interop:** Verwende die Android Leanback Library (`androidx.leanback`) nur zur Integration oder Pflege historischer Codebasen. Für Neuprojekte gilt **Compose for TV** als verbindlicher Standard.

### 1.3 Architektur
- **Clean Architecture + Unidirectional Data Flow (UDF) / MVVM / MVI**
  - **UI-Layer:** Jetpack Compose for TV Composables (strikte Trennung zwischen Stateful Screens und Stateless UI-Komponenten).
  - **Domain-Layer:** Business-Logik in reinen Kotlin `UseCases` / `Interactors` (völlig frei von Android Framework-Abhängigkeiten).
  - **Data-Layer:** Repository Pattern mit lokalen Caches (Room) und Remote Data Sources (Ktor / Retrofit).

### 1.4 Asynchrone Programmierung
- **Kotlin Coroutines & Flow** (`StateFlow`, `SharedFlow`).
- Verwende strikte **Dispatcher-Injektion** (`CoroutineDispatcher` via DI / Hilt), um Thread-Wechsel sauber testbar zu machen.

### 1.5 Media Playback (Jetpack Media3)
- Verwende **Jetpack Media3 (`androidx.media3:media3-exoplayer`)**.
- Unterstützung für **HLS (`.m3u8`)** und **DASH (`.mpd`)**.
- DRM-Integration via **Widevine Modular** (`MediaItem.DrmConfiguration`).
- Nahtlose Audio-Focus-Behandlung und Passthrough für Surrounding-Audio (Dolby Atmos / Digital Plus).

---

## 2. FIRE TV & 10-FOOT-UI SPECIFIC RULES (UX/UI Best Practices)

### 2.1 Focus Management & D-Pad Navigation
- **Regel 1 (Keine Touch-Annahmen):** Es gibt keinen Touchscreen! Jedes interaktive Element **MUSS** fokussierbar sein (`Modifier.focusable()` bzw. Compose TV Material3 Components wie `Button`, `Card`, `ListItem`).
- **Regel 2 (Explizite Focus-Pfade):** Bei komplexen Verschachtelungen (z. B. vertikale Listen mit horizontalen Karussells) nutze `FocusRequester` und `focusProperties { left = ...; right = ... }`, um unvorhersehbares D-Pad-Springen zu verhindern.
- **Regel 3 (Focus Restoring):** Wenn der Benutzer von einem Detail-Screen oder Suchmenü zurücknavigiert, **MUSS** der vorherige Fokus-Zustand exakt wiederhergestellt werden (z. B. via `FocusRestorer` oder ViewModel-State).
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
- **Regel 1 (Player Release):** Gebe den ExoPlayer/Media3 Player in `onStop()` **sofort frei** (`player.release()`), um Hardware-Video-Decoder im System nicht zu blockieren.
- **Regel 2 (Surface Management):** Stelle sicher, dass `PlayerView` oder `SurfaceView` beim Verlassen des Screens ordnungsgemäß abgemeldet werden.

### 4.2 Bild-Laden und Caching (Coil for TV)
- **Regel 1 (Memory Cache Limit):** Konfiguriere Coil mit explizitem RAM-Limit (max. 20% des verfügbaren Heap):
  ```kotlin
  val imageLoader = ImageLoader.Builder(context)
      .memoryCache {
          MemoryCache.Builder(context)
              .maxSizePercent(0.20)
              .build()
      }
      .bitmapConfig(Bitmap.Config.RGB_565) // 50% weniger Arbeitsspeicher als ARGB_8888 für Poster & Thumbnails!
      .crossfade(true)
      .build()
  ```
- **Regel 2 (Downscaling):** Lade Grafiken **NIEMALS** in Originalauflösung (z. B. 4K-Backdrops), sondern skaliere sie beim Netzwerkanforderungs-Request auf die exakt benötigten Pixel-Dimensionen.

### 4.3 Startzeit-Optimierung (Cold Start)
- **Regel 1 (Main Thread entlasten):** Keine synchronen I/O-Operations, Datenbankzugriffe oder schweren SDK-Initialisierungen auf dem Main-Thread.
- **Regel 2 (Baseline Profiles):** Generiere ein Baseline Profile (`androidx.profileinstaller`), um die JIT-Kompilierung beim App-Start und beim Scrollen durch lange Listen auf schwachen Fire TV CPUs zu minimieren.

### 4.4 Lifecycle & Power Management (Bildschirmschoner)
- **Screen Saver & Dimming:** Aktiviere `FLAG_KEEP_SCREEN_ON` **AUSSCHLIESSLICH** während der aktiven Videowiedergabe. Sobald das Video pausiert ist oder im Hauptmenü navigiert wird, MUSS die Flagge entfernt werden, damit der Fire OS-Bildschirmschoner anspringen kann.

---

## 5. CODING STANDARDS & QUALITY ASSURANCE RULES

### 5.1 Package-Struktur ("Package by Feature")
Organisiere den Code modular nach Features, nicht nach technischen Layer-Ordnern auf Top-Ebene:

```text
com.shadow.firetv/
├── core/
│   ├── designsystem/     # Compose TV Themes, Custom TV Components, Colors
│   ├── database/         # Room DB, DAOs
│   ├── network/          # Ktor/Retrofit Client & ADM Push Logic
│   └── player/           # Media3 ExoPlayer Wrapper
├── feature/
│   ├── home/             # Home Screen (Carousels, Hero Banner)
│   ├── details/          # VOD Details Screen
│   ├── player/           # Video Player UI & Overlay Controls
│   └── search/           # D-Pad Keyboard Search Screen
└── MainActivity.kt
```

### 5.2 Fehlerbehandlung & Offline-Feedback
- **Regel 1 (Keine "Silent Failures"):** Jeder Fehler muss abgefangen und dem Nutzer verständlich vermittelt werden.
- **Regel 2 (User-Friendly Error UI):** TV-Nutzer können keine URLs eintragen oder komplexe Formulare ausfüllen. Biete bei Netzwerkfehlern immer einen vorausgewählten D-Pad-Button an: `"Erneut versuchen"`.

### 5.3 Testing-Strategie
- **Unit Tests:** Teste ViewModels, Repositories und UseCases mit JUnit 5, MockK und `Turbine` (für Flow-Testing).
- **UI & D-Pad Tests:** Schreibe Compose UI-Tests mit synthetischen KeyEvents zur Überprüfung der Fokusführung:

  ```kotlin
  @Test
  fun testDPadNavigationToNextItem() {
      composeTestRule.onNodeWithTag("carousel_item_0").assertIsFocused()
      
      // Simuliere D-Pad Rechts-Taste
      composeTestRule.onRoot().performKeyInput { 
          pressKey(Key.DirectionRight) 
      }
      
      composeTestRule.onNodeWithTag("carousel_item_1").assertIsFocused()
  }
  ```
