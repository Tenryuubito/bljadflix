# CLAUDE.md

Bljadflix: Streaming-Client für Amazon Fire TV mit eigenem Python-Backend, das Serien-/Film-Kataloge von Drittseiten indexiert und Stream-URLs auflöst. UI-Texte, Logs und Fehlermeldungen sind auf Deutsch.

## Module

| Pfad | Inhalt |
|---|---|
| `app/` | Fire-TV-App (`com.shadow.firetv`), Kotlin, Jetpack Compose for TV |
| `installer/` | Launcher/Updater-App (`com.shadow.firetv.installer`), vergleicht die installierte Version mit `/app/version`, lädt die Haupt-App-APK und installiert sie. Kein Login. |
| `backend/` | FastAPI-Server, SQLite, Scraper/Indexer, Admin-Dashboard |
| `setup_server.sh` | Einrichtung auf Ubuntu-VPS (venv, `config.json`, systemd-Dienst `bljadflix`) |
| `start_backend.bat` | Backend lokal unter Windows starten |

## Befehle

Entwickelt wird unter Windows (Android Studio, JDK 17). `gradle.properties` setzt `org.gradle.java.home` auf einen Windows-Pfad, deshalb laufen Gradle-Befehle in WSL nicht.

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :installer:assembleDebug
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:testDebugUnitTest --tests "com.shadow.firetv.MainViewModelTest"
gradlew.bat :app:assembleDebug -PbackendUrl=http://10.0.0.5:8000/
```

Backend (Python 3, Abhängigkeiten in `backend/requirements.txt`, für Tests zusätzlich `httpx`):

```bash
cd backend
python app_backend.py
python indexer.py --source sto --limit 10
python indexer.py --full
python -m unittest discover -s tests -t .
python -m unittest tests.test_sto_indexer
```

`config.json` liegt im Projekt-Hauptverzeichnis (nicht in `backend/`), ist gitignored und Pflicht. Ohne `backend_port` beendet sich das Backend sofort. Ohne `admin_password` bleiben Dashboard und alle Admin-Routen gesperrt (503). Optional sind `backend_ip`, `default_source`, `indexer_interval_hours`, `sources`, `auto_ban_hours` (Dauer automatischer Sperren, Standard 24) und `trusted_proxies` (Liste von Proxy-IPs, nur von diesen wird `X-Forwarded-For` übernommen). Die Umgebungsvariablen `BLJADFLIX_CONFIG` und `BLJADFLIX_DB_DIR` überschreiben den Pfad zur Konfiguration bzw. zu den Datenbanken.

## CI/CD und Verteilung

`.github/workflows/deploy.yml` läuft bei jedem Push auf `main` und lässt sich auch manuell starten:

1. `backend-tests`: Backend-Unit-Tests.
2. `android-build`: Android-Unit-Tests, danach signierte Release-APKs von App und Installer. Die Backend-Adresse kommt aus der Repository-Variable `BACKEND_URL`, `versionCode` ist die GitHub-Run-Nummer, `versionName` lautet `1.0.<run>`. Der Job schreibt außerdem `version.json`.
3. `deploy` (nur wenn 1 und 2 grün sind): überträgt `backend/` per rsync nach `~/bljadflix/backend/` auf der Google-Cloud-VM. `*.db`, `config.json` und `updates/` bleiben dabei unangetastet. Danach werden APKs und `version.json` nach `backend/updates/` verschoben, `pip install` ausgeführt, `bljadflix` neu gestartet und `/app/version` geprüft.

Benötigte Secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, optional `DEPLOY_KNOWN_HOSTS`, außerdem `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` und `SIGNING_KEY_PASSWORD`. Variable: `BACKEND_URL`. Lokal gebaute Release-APKs werden nur signiert, wenn `SIGNING_KEYSTORE_PATH` gesetzt ist. Den Keystore niemals wechseln, sonst lassen sich installierte Apps nicht mehr aktualisieren.

Erstinstallation auf einem Fire TV: In der App „Downloader“ `http://<server>:8000/install` öffnen. Das leitet auf die Installer-APK weiter.

Update-Ablauf: Die Haupt-App fragt bei jedem `onResume` von `MainActivity` `/app/version` ab (`feature/update/UpdateViewModel`). Ist `mainApp.versionCode` höher als der eigene, erscheint nach dem Splash ein Dialog. „Jetzt aktualisieren“ startet den Installer und beendet die App. Der Installer arbeitet beim Start automatisch (`AutoMode.FULL`): Ist `launcher.versionCode` höher als der eigene, lädt und installiert er zuerst sich selbst, danach die Haupt-App. Ist alles aktuell, startet er die Haupt-App und beendet sich. Nach der Rückkehr aus dem System-Installationsdialog prüft er erneut (`AutoMode.LAUNCH_ONLY`), damit ein abgebrochenes Update keine Endlosschleife erzeugt. Die Bestätigung im Android-Installationsdialog lässt sich bei Sideload-Apps nicht umgehen. Der Installer braucht die Freigabe „Apps unbekannter Herkunft“.

## Android-App

- Architektur: MVVM mit `StateFlow` (UI-State) und `SharedFlow` (Einmal-Events wie Toasts und Navigation). ViewModels liegen unter `feature/<name>/`, die zugehörigen Activities teilweise noch im Root-Paket (`MainActivity`, `SeriesDetailActivity`, `PlayerActivity`).
- Kein DI-Framework: Jedes ViewModel hat eine verschachtelte `Factory`, deren Default-Parameter die echten Abhängigkeiten liefern. Tests übergeben Mocks direkt an den Konstruktor.
- `AuthManager`, `SourceManager` und `ThemeManager` sind Singletons über `getInstance(context)` und speichern in SharedPreferences. Für Tests gibt es `createForTesting(prefs)`. Initialisiert werden sie in `BljadflixApplication`.
- Netzwerk: `RetrofitClient` nutzt `BuildConfig.BACKEND_URL` (Gradle-Property `backendUrl`, Standard in `app/build.gradle.kts`). `AuthInterceptor` setzt `Authorization: Bearer` und `X-Device-ID` und löscht die Session bei 401/409. `MovieRepositoryImpl` kapselt jeden Aufruf in `safeApiCall` → `Result<T>` und übernimmt `detail` aus FastAPI-Fehlerantworten als Fehlermeldung.
- DTOs in `core/model/Models.kt` verwenden snake_case-Feldnamen, damit Gson ohne Annotationen auf das Backend-JSON passt.
- Quelle (`StreamingSource`: aniworld, sto, filmo): Das Repository setzt sie als `source`-Query-Parameter bzw. als `source`-Feld in `ProgressRequest`/`WatchlistRequest`. Methoden mit optionalem `source`-Parameter fallen auf die aktive Quelle zurück. Inhalte aus Watchlist und „Weiterschauen“ können aus einer anderen Quelle stammen, deshalb wird ihre Quelle als Intent-Extra `SOURCE` an Detail- und Player-Screen weitergegeben.
- Player: Es gibt kein ExoPlayer. `PlayerActivity` lädt die aufgelöste Hoster-URL in eine WebView und steuert `<video>` bzw. jwplayer per injiziertem JavaScript. Fortschritt wird periodisch an `/progress` gemeldet, ab 90 % gilt eine Folge als gesehen. Beim Start springt der Player an die gespeicherte Position (außer bei gesehenen Folgen), 10 s vor Schluss läuft ein Countdown zur nächsten Folge (abbrechbar mit Zurück). Tasten: ◀/▶ und Rewind/FF spulen beschleunigt (`SeekAccelerator`), ▲/Menü öffnet das Options-Panel (Folge wechseln, Stream neu laden, anderen Hoster über `HosterLinkResolver.resolve(url, skip)`), Stream-Fehler zeigen dasselbe Panel statt den Player zu schließen. Werbung blockt `core/stream/AdBlocker`: Anfragen an Werbe-Hosts liefern leere Antworten, Pop-ups sind aus, Seitenwechsel auf fremde Domains werden blockiert, sobald das Video läuft, und das injizierte JavaScript blendet alles außerhalb des Videos aus.
- Sidebar-Einträge in `MainViewModel` sind deutsche Strings mit Emoji (`"📺 Weiterschauen"`, `"⭐ Meine Watchlist"` …) und werden per String-Vergleich ausgewertet. Bei Umbenennungen alle Stellen inklusive Tests anpassen. Die Einstellungen sind kein Sidebar-Eintrag, sondern das Zahnrad-`IconButton` neben dem App-Namen in der Sidebar (`MainViewModel.openSettings()`).
- UI: Jetpack Compose for TV (`androidx.tv:tv-material`). Jede Activity ruft `setContent { BljadflixTheme { … } }` auf, die Screens liegen als `<Name>Screen.kt` neben ihrem ViewModel unter `feature/<name>/`. `core/ui/theme/BljadflixTheme.kt` bildet die `AppTheme`-Werte auf das TV-Farbschema ab (`BljadflixColors` für feste Farben, `ScreenBackground` für den Verlauf). Gemeinsame Bausteine in `core/ui/components/`: `CoverImage`, `TvTextField` (Tastatur erst nach OK), `TvActionButton`, `TvDialog`, `LinearProgress`, `LoadingIndicator`. Der Player bleibt eine WebView (`AndroidView`) mit Compose-Overlay (`feature/player/PlayerOverlay.kt`), seine Tasten wertet weiterhin `PlayerActivity.dispatchKeyEvent` aus.
- D-Pad-Fokus: TV-Material-Komponenten bringen Skalierung und Rahmen beim Fokus mit. Fokuspfade werden mit `FocusRequester`, `focusRestorer()`, `focusProperties { enter = … }` und `onPreviewKeyEvent` gesteuert (z. B. Sidebar ↔ Raster, Suche ↔ Treffer, Staffel-Tabs → WEITER-Folge). Jedes interaktive Element muss fokussierbar bleiben.
- Cover lädt ausschließlich `core/ui/components/CoverImage` (Glide-Compose) über das Backend (`/cover/{source}/{content_id}`, URL aus `CoverLoader.coverUrl`), nie direkt von den Quellseiten, weil diese in deutschen Heimnetzen per DNS gesperrt sein können. Glide nutzt `DecodeFormat.PREFER_RGB_565`, um auf Fire-TV-Sticks Speicher zu sparen. Fehlt ein Bild oder schlägt das Laden fehl, erscheint `placeholder_cover`.

## Backend

- `app_backend.py` erzeugt alle Services als Modul-Globals und definiert sämtliche Routen. Logik gehört in `services/`, Pydantic-Modelle in `models/`.
- Listen-Endpunkte (`/movies`, `/movies/genre/…`, `/search`, Watchlist, Weiterschauen) müssen `source` in jedem Eintrag mitliefern. Die App lädt Cover, Episoden und Streams mit genau dieser Quelle, eine falsche Quelle zeigt fremde Serien.
- Datenbanken (SQLite, WAL) liegen in `backend/`: `users.db` für Nutzer, Sessions, Fortschritt, Watchlist, Logs und Sperren, dazu je Quelle `movies_<source>.db` (`content_index`, `episodes`, `video_links`). Content-IDs sind nur innerhalb einer Quelle eindeutig, deshalb trägt jede Nutzer-Tabelle eine `source`-Spalte. `DatabaseManager.sanitize_source` bereinigt Quellnamen und fällt auf `aniworld` zurück.
- Schema-Änderungen laufen nur über `CREATE TABLE IF NOT EXISTS` in `DatabaseManager.init_db()`. Migrationen gibt es keine.
- Indexer: `services/indexers/<quelle>_indexer.py` erben von `BaseIndexer` (Retry, rotierende User-Agents, `save_series_to_db`) und implementieren `process_single_series(url)` und `scrape_all_series(limit, incremental)`. `IndexerService` verteilt nach Quelle, `SchedulerService` startet alle `indexer_interval_hours` (Standard 12) einen Lauf. Fortschritt landet in `indexer_logger` und erscheint live im Dashboard. Einzelne Serien werden beim Abruf der Episoden und beim Sync höchstens alle 6 h neu gescrapt (`SERIES_REFRESH_SECONDS`), nur `POST /movies/{id}/reindex` erzwingt einen sofortigen Lauf.
- `StreamService.resolve_stream` folgt Redirects und iframes bis zur Hoster- oder Medien-URL und speichert das Ergebnis 2 h in `video_links`.
- Nutzer-Login: Benutzername plus Passwort oder Anmeldecode, alternativ nur der Anmeldecode. Benutzername allein reicht nicht. Passwörter werden als PBKDF2-Hash gespeichert (`passwords.py`), noch vorhandene Klartext-Passwörter wandelt `init_db()` beim Start um. Standardnutzer werden nicht mehr angelegt, Nutzer entstehen über das Dashboard.
- „Weiterschauen“-Einträge blendet `DELETE /continue-watching/{content_id}` über die Tabelle `hidden_continue_watching` aus. Der Fortschritt bleibt erhalten, und sobald die Serie erneut geschaut wird, erscheint sie wieder.
- Eine aktive Session pro Nutzer. Ein Login auf einem anderen Gerät ersetzt die Session, das alte Gerät bekommt 409, ebenso Anfragen ohne passende `X-Device-ID`. Nach 3 Fehlversuchen werden IP und Gerät für `auto_ban_hours` gesperrt (`banned_devices`, erkennbar am Grund `AUTO_BAN_REASON`), manuelle Sperren aus dem Dashboard gelten dauerhaft. Die Middleware blockt gesperrte Clients bei jedem Request.
- Admin-Dashboard: `/dashboard` liefert das HTML aus `dashboard_view.py` (Tailwind, Chart.js, Vanilla JS). Die Anmeldung erfolgt mit `admin_password` über `POST /admin/api/login`, das ein Token zurückgibt (12 h gültig, nur im Speicher, nach 5 Fehlversuchen 15 min Sperre pro IP). Das Dashboard speichert es in `sessionStorage` und schickt es als `X-Admin-Token` mit. Alle `/admin/*`-Routen außer Login, Logout und Dashboard-Seite hängen an `admin_router` mit `Depends(require_admin)`. `/admin/trigger-index` akzeptiert auch ein normales Nutzer-Token, weil die TV-App den Indexer startet. Neue Admin-Routen gehören an `admin_router`, nicht an `app`.
- Datenbank-Explorer: Tabellennamen werden gegen `sqlite_master` geprüft und Bezeichner mit `_quote_identifier` maskiert. Niemals Namen ungeprüft in SQL einsetzen.
- Cover (öffentlich, ohne Login): `/cover/{source}/{content_id}` schlägt die `cover_url` in der Datenbank nach (keine frei wählbaren URLs), lädt das Bild, verkleinert es mit Pillow auf 300 px Breite und legt es in `backend/image_cache/` ab. Der Ordner ist gitignored und vom Deploy-rsync ausgenommen.
- App-Verteilung (öffentlich, ohne Login): `/app/version` liest `backend/updates/version.json`. `/app/download/main` und `/app/download/launcher` liefern die APKs aus `backend/updates/` und fallen sonst auf die Debug-Build-Ausgaben zurück.

## Tests

- Android: JUnit 4, MockK, kotlinx-coroutines-test (`MainDispatcherRule`), Turbine. Die Tests liegen flach in `app/src/test/java/com/shadow/firetv/`.
- Backend: `unittest`. `tests/__init__.py` setzt `BLJADFLIX_DB_DIR` und `BLJADFLIX_CONFIG` auf ein temporäres Verzeichnis (Admin-Passwort: `tests.TEST_ADMIN_PASSWORD`), bevor `app_backend` importiert wird. Testmodule, die `app_backend` importieren, müssen vorher `import tests` ausführen, damit sie auch einzeln nie die echten Datenbanken anfassen. Die Indexer- und Stream-Tests mocken HTTP.

## Konventionen

- Keine Kommentare im Code.
- Neue Ressourcen nur anlegen, wenn sie auch referenziert werden. Ungenutzte Strings, Farben und Layouts wurden entfernt.
- Datenbankdateien (`*.db`) und `config.json` niemals committen.
- Die Backend-Adresse für App und Installer kommt aus `BuildConfig.BACKEND_URL`. Der Standardwert steht in `app/build.gradle.kts` und `installer/build.gradle.kts`, überschreiben lässt er sich mit `-PbackendUrl=...`.
- `.cursorrules` und `FIRE_TV_RULES.md` enthalten die Fire-TV-Regeln (D-Pad-Fokus, keine GMS-Abhängigkeiten, Overscan-Ränder, Schriftgrößen) und gelten zusätzlich zu dieser Datei.
