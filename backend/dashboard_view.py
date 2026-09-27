def get_dashboard_html() -> str:
    return """<!DOCTYPE html>
<html lang="de" class="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bljadflix Backend - Admin Dashboard</title>
    <!-- Tailwind CSS CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
        tailwind.config = {
            darkMode: 'class',
            theme: {
                extend: {
                    colors: {
                        darkbg: '#0f172a',
                        cardbg: '#1e293b',
                        cardborder: '#334155',
                        accentred: '#e11d48',
                        accentgold: '#f59e0b',
                        accentblue: '#3b82f6',
                        accentgreen: '#10b981'
                    }
                }
            }
        }
    </script>
    <!-- Chart.js CDN -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <!-- Lucide Icons -->
    <script src="https://unpkg.com/lucide@latest"></script>
    <style>
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-track { background: #0f172a; }
        ::-webkit-scrollbar-thumb { background: #334155; border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: #475569; }
    </style>
</head>
<body class="bg-darkbg text-slate-100 font-sans min-h-screen flex flex-col">

    <!-- Header / Navbar -->
    <header class="bg-cardbg border-b border-cardborder px-6 py-4 flex flex-wrap items-center justify-between shadow-lg sticky top-0 z-50">
        <div class="flex items-center space-x-3">
            <div class="w-10 h-10 rounded-lg bg-gradient-to-tr from-accentred to-amber-500 flex items-center justify-center font-bold text-xl shadow-md text-white">
                🎬
            </div>
            <div>
                <h1 class="text-xl font-bold tracking-wide text-white">Bljadflix Dashboard</h1>
                <p class="text-xs text-slate-400">Multi-Source Stream Backend & Database Manager</p>
            </div>
        </div>

        <!-- Navigation Tabs -->
        <nav class="flex space-x-1 bg-slate-900/60 p-1 rounded-xl border border-cardborder mt-3 sm:mt-0">
            <button onclick="switchTab('dashboard')" id="tab-btn-dashboard" class="nav-tab text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-2 transition bg-accentred text-white shadow">
                <i data-lucide="layout-dashboard" class="w-4 h-4"></i>
                <span>Dashboard</span>
            </button>
            <button onclick="switchTab('activity')" id="tab-btn-activity" class="nav-tab text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-2 text-slate-400 hover:text-white transition">
                <i data-lucide="activity" class="w-4 h-4"></i>
                <span>Aktivitäts-Log</span>
            </button>
            <button onclick="switchTab('users')" id="tab-btn-users" class="nav-tab text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-2 text-slate-400 hover:text-white transition">
                <i data-lucide="users" class="w-4 h-4"></i>
                <span>Benutzer</span>
            </button>
            <button onclick="switchTab('database')" id="tab-btn-database" class="nav-tab text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-2 text-slate-400 hover:text-white transition">
                <i data-lucide="database" class="w-4 h-4"></i>
                <span>DB Explorer</span>
            </button>
            <button onclick="switchTab('scraper')" id="tab-btn-scraper" class="nav-tab text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-2 text-slate-400 hover:text-white transition">
                <i data-lucide="refresh-cw" class="w-4 h-4"></i>
                <span>Scraper</span>
            </button>
            <button onclick="switchTab('banned')" id="tab-btn-banned" class="nav-tab text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-2 text-slate-400 hover:text-white transition">
                <i data-lucide="shield-alert" class="w-4 h-4"></i>
                <span>Gesperrte Geräte</span>
            </button>
        </nav>
    </header>

    <!-- Main Content Container -->
    <main class="flex-1 p-6 max-w-7xl w-full mx-auto space-y-6">

        <!-- TAB 1: DASHBOARD & STATS -->
        <div id="tab-content-dashboard" class="space-y-6">
            <!-- Stat Cards -->
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow flex items-center justify-between">
                    <div>
                        <p class="text-xs font-medium text-slate-400 uppercase tracking-wider">Benutzer / Sitzungen</p>
                        <h3 class="text-2xl font-bold text-white mt-1" id="stat-users-count">-</h3>
                        <p class="text-xs text-emerald-400 mt-1" id="stat-active-sessions">-</p>
                    </div>
                    <div class="p-3 bg-blue-500/10 rounded-xl text-blue-400">
                        <i data-lucide="users" class="w-7 h-7"></i>
                    </div>
                </div>

                <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow flex items-center justify-between">
                    <div>
                        <p class="text-xs font-medium text-slate-400 uppercase tracking-wider">Gesehen (Stunden)</p>
                        <h3 class="text-2xl font-bold text-amber-400 mt-1" id="stat-watch-hours">-</h3>
                        <p class="text-xs text-slate-400 mt-1" id="stat-watch-events">- Events</p>
                    </div>
                    <div class="p-3 bg-amber-500/10 rounded-xl text-amber-400">
                        <i data-lucide="clock" class="w-7 h-7"></i>
                    </div>
                </div>

                <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow flex items-center justify-between">
                    <div>
                        <p class="text-xs font-medium text-slate-400 uppercase tracking-wider">AniWorld / S.to / Filmo</p>
                        <h3 class="text-2xl font-bold text-white mt-1" id="stat-content-total">-</h3>
                        <p class="text-xs text-slate-400 mt-1" id="stat-source-counts">Inhalte indiziert</p>
                    </div>
                    <div class="p-3 bg-rose-500/10 rounded-xl text-rose-400">
                        <i data-lucide="tv" class="w-7 h-7"></i>
                    </div>
                </div>

                <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow flex items-center justify-between">
                    <div>
                        <p class="text-xs font-medium text-slate-400 uppercase tracking-wider">Abgeschlossene Folgen</p>
                        <h3 class="text-2xl font-bold text-emerald-400 mt-1" id="stat-completed-count">-</h3>
                        <p class="text-xs text-slate-400 mt-1" id="stat-completion-rate">- Quote</p>
                    </div>
                    <div class="p-3 bg-emerald-500/10 rounded-xl text-emerald-400">
                        <i data-lucide="check-circle-2" class="w-7 h-7"></i>
                    </div>
                </div>
            </div>

            <!-- Charts Row 1 -->
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <!-- Timeline Chart -->
                <div class="lg:col-span-2 bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                    <div class="flex items-center justify-between">
                        <h3 class="font-semibold text-white flex items-center space-x-2">
                            <i data-lucide="line-chart" class="w-5 h-5 text-accentblue"></i>
                            <span>Wiedergabe-Aktivität (Letzte Tage)</span>
                        </h3>
                    </div>
                    <div class="h-64 relative">
                        <canvas id="timelineChart"></canvas>
                    </div>
                </div>

                <!-- Source Ratio Doughnut -->
                <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4 flex flex-col justify-between">
                    <h3 class="font-semibold text-white flex items-center space-x-2">
                        <i data-lucide="pie-chart" class="w-5 h-5 text-amber-400"></i>
                        <span>Quellen Verteilung</span>
                    </h3>
                    <div class="h-56 relative flex items-center justify-center">
                        <canvas id="sourceChart"></canvas>
                    </div>
                </div>
            </div>

            <!-- Top Watched Content Table -->
            <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                <div class="flex items-center justify-between">
                    <h3 class="font-semibold text-white flex items-center space-x-2">
                        <i data-lucide="flame" class="w-5 h-5 text-rose-500"></i>
                        <span>Meistgeschaute Serien & Filme</span>
                    </h3>
                </div>
                <div class="overflow-x-auto">
                    <table class="w-full text-left text-sm text-slate-300">
                        <thead class="bg-slate-900/60 text-slate-400 uppercase text-xs">
                            <tr>
                                <th class="p-3 rounded-l-lg">Titel</th>
                                <th class="p-3">Quelle</th>
                                <th class="p-3">Genre</th>
                                <th class="p-3 text-center">Zuschauer</th>
                                <th class="p-3 text-center">Gesamt-Aufrufe</th>
                                <th class="p-3 text-right rounded-r-lg">Wiedergabezeit</th>
                            </tr>
                        </thead>
                        <tbody id="top-content-tbody" class="divide-y divide-slate-800">
                            <tr><td colspan="6" class="p-4 text-center text-slate-500">Lade Statistiken...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- TAB 2: ACTIVITÄTS-LOG ("Wer schaut was") -->
        <div id="tab-content-activity" class="space-y-6 hidden">
            <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                <div class="flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h2 class="text-lg font-bold text-white flex items-center space-x-2">
                            <i data-lucide="history" class="w-5 h-5 text-accentred"></i>
                            <span>Echtzeit Wiedergabe-Protokoll</span>
                        </h2>
                        <p class="text-xs text-slate-400">Wer schaut gerade was, wann und mit welchem Fortschritt</p>
                    </div>

                    <!-- Filters -->
                    <div class="flex flex-wrap items-center gap-3">
                        <select id="log-filter-source" onchange="loadActivityLogs()" class="bg-slate-900 border border-cardborder text-slate-200 text-xs rounded-xl px-3 py-2 outline-none">
                            <option value="all">Alle Quellen</option>
                            <option value="aniworld">AniWorld</option>
                            <option value="sto">S.to</option>
                            <option value="filmo">Filmo</option>
                        </select>
                        <input type="text" id="log-filter-search" oninput="debounce(loadActivityLogs, 300)()" placeholder="Suchbegriff (Nutzer/Titel)..." class="bg-slate-900 border border-cardborder text-slate-200 text-xs rounded-xl px-3 py-2 outline-none w-48">
                        <button onclick="loadActivityLogs()" class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs px-3 py-2 rounded-xl border border-cardborder flex items-center space-x-1">
                            <i data-lucide="rotate-cw" class="w-3.5 h-3.5"></i>
                            <span>Aktualisieren</span>
                        </button>
                    </div>
                </div>

                <!-- Activity Log Table -->
                <div class="overflow-x-auto">
                    <table class="w-full text-left text-sm text-slate-300">
                        <thead class="bg-slate-900/60 text-slate-400 uppercase text-xs">
                            <tr>
                                <th class="p-3 rounded-l-lg">Zeitpunkt</th>
                                <th class="p-3">Nutzer</th>
                                <th class="p-3">Inhalt / Serie</th>
                                <th class="p-3">Folge / Titel</th>
                                <th class="p-3">Fortschritt</th>
                                <th class="p-3 text-right rounded-r-lg">Quelle</th>
                            </tr>
                        </thead>
                        <tbody id="activity-logs-tbody" class="divide-y divide-slate-800">
                            <tr><td colspan="6" class="p-4 text-center text-slate-500">Lade Protokolle...</td></tr>
                        </tbody>
                    </table>
                </div>

                <!-- Pagination -->
                <div class="flex items-center justify-between text-xs text-slate-400 pt-2" id="log-pagination">
                    <span id="log-page-info">Seite 1</span>
                    <div class="space-x-2">
                        <button id="btn-log-prev" onclick="changeLogPage(-1)" class="px-3 py-1 bg-slate-800 border border-cardborder rounded-lg disabled:opacity-50">Zurück</button>
                        <button id="btn-log-next" onclick="changeLogPage(1)" class="px-3 py-1 bg-slate-800 border border-cardborder rounded-lg disabled:opacity-50">Weiter</button>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 3: BENUTZER & PROFILE -->
        <div id="tab-content-users" class="space-y-6 hidden">
            <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                <div class="flex items-center justify-between">
                    <div>
                        <h2 class="text-lg font-bold text-white flex items-center space-x-2">
                            <i data-lucide="user-check" class="w-5 h-5 text-accentblue"></i>
                            <span>Benutzerkonten & Profile</span>
                        </h2>
                        <p class="text-xs text-slate-400">Nutzerdaten, Login-Codes, Gerätesitzungen und Aktivität</p>
                    </div>
                    <button onclick="openCreateUserModal()" class="bg-accentred hover:bg-rose-700 text-white text-xs font-semibold px-4 py-2 rounded-xl flex items-center space-x-2 shadow">
                        <i data-lucide="user-plus" class="w-4 h-4"></i>
                        <span>Neuer Benutzer</span>
                    </button>
                </div>

                <div class="overflow-x-auto">
                    <table class="w-full text-left text-sm text-slate-300">
                        <thead class="bg-slate-900/60 text-slate-400 uppercase text-xs">
                            <tr>
                                <th class="p-3 rounded-l-lg">ID</th>
                                <th class="p-3">Benutzername</th>
                                <th class="p-3">Anmeldecode</th>
                                <th class="p-3">Status</th>
                                <th class="p-3">Aktives Gerät</th>
                                <th class="p-3 text-center">Gesehene Folgen</th>
                                <th class="p-3 text-right rounded-r-lg">Aktionen</th>
                            </tr>
                        </thead>
                        <tbody id="users-tbody" class="divide-y divide-slate-800">
                            <tr><td colspan="7" class="p-4 text-center text-slate-500">Lade Benutzerdaten...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- TAB 4: DATENBANK EXPLORER (phpMyAdmin style) -->
        <div id="tab-content-database" class="space-y-6 hidden">
            <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                <div class="flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h2 class="text-lg font-bold text-white flex items-center space-x-2">
                            <i data-lucide="database" class="w-5 h-5 text-amber-400"></i>
                            <span>SQLite Datenbank Explorer</span>
                        </h2>
                        <p class="text-xs text-slate-400">Inspektion und SQL-Abfragen wie bei phpMyAdmin</p>
                    </div>

                    <div class="flex items-center space-x-3">
                        <label class="text-xs text-slate-400 font-medium">Datenbank wählen:</label>
                        <select id="db-selector" onchange="loadDatabaseTables()" class="bg-slate-900 border border-cardborder text-white text-xs rounded-xl px-4 py-2 font-semibold outline-none">
                            <option value="users">users.db (Benutzer & Fortgabe)</option>
                            <option value="aniworld">movies_aniworld.db (AniWorld)</option>
                            <option value="sto">movies_sto.db (S.to)</option>
                            <option value="filmo">movies_filmo.db (Filmo)</option>
                        </select>
                    </div>
                </div>

                <!-- DB Layout Grid -->
                <div class="grid grid-cols-1 lg:grid-cols-4 gap-6">
                    <!-- Left Sidebar: Tables List -->
                    <div class="bg-slate-900/80 border border-cardborder p-4 rounded-xl space-y-3">
                        <h3 class="text-xs font-semibold uppercase text-slate-400 tracking-wider">Tabellen</h3>
                        <div id="db-tables-list" class="space-y-1">
                            <div class="text-xs text-slate-500 p-2">Lade Tabellen...</div>
                        </div>
                    </div>

                    <!-- Right Main Area: Table Data & Query Console -->
                    <div class="lg:col-span-3 space-y-4">
                        <!-- Mode Tabs -->
                        <div class="flex space-x-2 border-b border-cardborder pb-2">
                            <button onclick="setDbSubTab('data')" id="dbtab-data" class="text-xs font-semibold px-3 py-1.5 rounded-lg bg-slate-800 text-white">
                                Data Browser
                            </button>
                            <button onclick="setDbSubTab('query')" id="dbtab-query" class="text-xs font-semibold px-3 py-1.5 rounded-lg text-slate-400 hover:text-white">
                                SQL Konsole
                            </button>
                        </div>

                        <!-- Sub-tab 1: Data Browser -->
                        <div id="dbsub-data" class="space-y-3">
                            <div class="flex items-center justify-between gap-3">
                                <span class="text-xs font-semibold text-amber-400" id="current-table-title">Tabelle wählen...</span>
                                <input type="text" id="db-search-input" oninput="debounce(loadTableData, 300)()" placeholder="Tabelle durchsuchen..." class="bg-slate-900 border border-cardborder text-slate-200 text-xs rounded-xl px-3 py-1.5 outline-none w-48">
                            </div>

                            <div class="overflow-x-auto border border-cardborder rounded-xl">
                                <table class="w-full text-left text-xs text-slate-300">
                                    <thead id="db-table-thead" class="bg-slate-900 text-slate-400 uppercase font-semibold">
                                        <tr><th class="p-3">Spalten</th></tr>
                                    </thead>
                                    <tbody id="db-table-tbody" class="divide-y divide-slate-800">
                                        <tr><td class="p-4 text-center text-slate-500">Keine Tabelle ausgewählt.</td></tr>
                                    </tbody>
                                </table>
                            </div>

                            <div class="flex items-center justify-between text-xs text-slate-400" id="db-pagination">
                                <span id="db-page-info">Seite 1</span>
                                <div class="space-x-2">
                                    <button id="btn-db-prev" onclick="changeDbPage(-1)" class="px-3 py-1 bg-slate-800 border border-cardborder rounded-lg disabled:opacity-50">Zurück</button>
                                    <button id="btn-db-next" onclick="changeDbPage(1)" class="px-3 py-1 bg-slate-800 border border-cardborder rounded-lg disabled:opacity-50">Weiter</button>
                                </div>
                            </div>
                        </div>

                        <!-- Sub-tab 2: SQL Console -->
                        <div id="dbsub-query" class="space-y-3 hidden">
                            <textarea id="sql-query-input" rows="4" class="w-full bg-slate-950 border border-cardborder text-emerald-400 font-mono text-xs p-3 rounded-xl outline-none focus:border-amber-500" placeholder="SELECT * FROM content_index LIMIT 20;"></textarea>
                            <button onclick="runSqlQuery()" class="bg-amber-500 hover:bg-amber-600 text-slate-950 font-bold text-xs px-4 py-2 rounded-xl flex items-center space-x-2">
                                <i data-lucide="play" class="w-4 h-4"></i>
                                <span>SQL Ausführen</span>
                            </button>
                            <div id="sql-query-result" class="overflow-x-auto border border-cardborder rounded-xl p-2 bg-slate-900 text-xs">
                                <span class="text-slate-500">Ergebnis erscheint hier...</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 5: SCRAPER & SYSTEM -->
        <div id="tab-content-scraper" class="space-y-6 hidden">
            <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                <h2 class="text-lg font-bold text-white flex items-center space-x-2">
                    <i data-lucide="refresh-cw" class="w-5 h-5 text-emerald-400"></i>
                    <span>Scraper & Indexer Konsole</span>
                </h2>
                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                    <button onclick="triggerIndex('all')" class="bg-slate-900 hover:bg-slate-800 border border-cardborder p-3.5 rounded-xl text-left space-y-1.5 group transition">
                        <div class="text-accentblue font-bold text-xs group-hover:text-blue-400 flex items-center space-x-1">
                            <i data-lucide="play" class="w-3.5 h-3.5"></i>
                            <span>Alle Quellen Indexieren</span>
                        </div>
                        <p class="text-[11px] text-slate-400">AniWorld, S.to & Filmo parallel verarbeiten.</p>
                    </button>

                    <button onclick="triggerIndex('aniworld')" class="bg-slate-900 hover:bg-slate-800 border border-cardborder p-3.5 rounded-xl text-left space-y-1.5 group transition">
                        <div class="text-blue-400 font-bold text-xs group-hover:text-blue-300 flex items-center space-x-1">
                            <i data-lucide="tv" class="w-3.5 h-3.5"></i>
                            <span>AniWorld Indexieren</span>
                        </div>
                        <p class="text-[11px] text-slate-400">Nur Anime-Katalog abgleichen.</p>
                    </button>

                    <button onclick="triggerIndex('sto')" class="bg-slate-900 hover:bg-slate-800 border border-cardborder p-3.5 rounded-xl text-left space-y-1.5 group transition">
                        <div class="text-amber-400 font-bold text-xs group-hover:text-amber-300 flex items-center space-x-1">
                            <i data-lucide="film" class="w-3.5 h-3.5"></i>
                            <span>S.to Indexieren</span>
                        </div>
                        <p class="text-[11px] text-slate-400">Nur S.to Serien abgleichen.</p>
                    </button>

                    <button onclick="triggerIndex('filmo')" class="bg-slate-900 hover:bg-slate-800 border border-cardborder p-3.5 rounded-xl text-left space-y-1.5 group transition">
                        <div class="text-emerald-400 font-bold text-xs group-hover:text-emerald-300 flex items-center space-x-1">
                            <i data-lucide="clapperboard" class="w-3.5 h-3.5"></i>
                            <span>Filmo Indexieren</span>
                        </div>
                        <p class="text-[11px] text-slate-400">Nur Filmo Inhalte abgleichen.</p>
                    </button>
                </div>

                <!-- Live Terminal Box -->
                <div class="space-y-3 pt-2">
                    <div class="flex items-center justify-between">
                        <div class="flex items-center space-x-2">
                            <span class="text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center space-x-1.5">
                                <i data-lucide="terminal" class="w-4 h-4 text-emerald-400"></i>
                                <span>Live Terminal Ausgabe</span>
                            </span>
                            <span id="indexer-status-badge" class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-slate-800 text-slate-400">INAKTIV</span>
                        </div>
                        <div class="flex items-center space-x-3 text-xs">
                            <label class="flex items-center space-x-1.5 cursor-pointer text-slate-400 hover:text-white select-none">
                                <input type="checkbox" id="chk-autoscroll" checked class="rounded bg-slate-900 border-cardborder text-emerald-400 focus:ring-0">
                                <span>Auto-Scroll</span>
                            </label>
                            <button onclick="clearTerminalLogs()" class="bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs px-2.5 py-1 rounded-lg border border-cardborder flex items-center space-x-1 transition">
                                <i data-lucide="trash-2" class="w-3.5 h-3.5"></i>
                                <span>Terminal leeren</span>
                            </button>
                        </div>
                    </div>

                    <div id="terminal-console" class="p-4 bg-slate-950 border border-cardborder rounded-2xl font-mono text-xs text-slate-200 h-[380px] overflow-y-auto space-y-1 shadow-inner leading-relaxed">
                        <div class="text-slate-500 italic">[SYSTEM] Terminal bereit. Starte eine Indexierung, um den Echtzeit-Fortschritt zu verfolgen.</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 6: GESPERRTE GERÄTE & IP-SPERREN -->
        <div id="tab-content-banned" class="space-y-6 hidden">
            <div class="bg-cardbg border border-cardborder p-5 rounded-2xl shadow space-y-4">
                <div class="flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h2 class="text-lg font-bold text-white flex items-center space-x-2">
                            <i data-lucide="shield-alert" class="w-5 h-5 text-rose-500"></i>
                            <span>Gesperrte Geräte & IP-Adressen</span>
                        </h2>
                        <p class="text-xs text-slate-400">Automatisch gesperrte Geräte (nach 3 fehlgeschlagenen Logins) & manuelle Sperren</p>
                    </div>

                    <button onclick="openManualBanModal()" class="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs rounded-xl shadow flex items-center space-x-2">
                        <i data-lucide="ban" class="w-4 h-4"></i>
                        <span>Gerät / IP manuell sperren</span>
                    </button>
                </div>

                <div class="overflow-x-auto border border-cardborder rounded-xl">
                    <table class="w-full text-left text-sm text-slate-300">
                        <thead class="bg-slate-900/80 text-slate-400 uppercase text-xs font-semibold">
                            <tr>
                                <th class="p-3">IP-Adresse</th>
                                <th class="p-3">Standort</th>
                                <th class="p-3">Gerät / ID</th>
                                <th class="p-3">User Agent</th>
                                <th class="p-3 text-center">Fehlschläge</th>
                                <th class="p-3">Gesperrt am</th>
                                <th class="p-3">Letzter Versuch</th>
                                <th class="p-3">Grund</th>
                                <th class="p-3 text-right">Aktion</th>
                            </tr>
                        </thead>
                        <tbody id="banned-devices-tbody" class="divide-y divide-slate-800">
                            <tr><td colspan="9" class="p-4 text-center text-slate-500">Lade gesperrte Geräte...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

    </main>

    <!-- User Details Modal (Genaue Nutzerdetails with Charts & Stats) -->
    <div id="user-details-modal" class="fixed inset-0 bg-slate-950/85 backdrop-blur-md hidden flex items-center justify-center p-4 z-50 overflow-y-auto">
        <div class="bg-cardbg border border-cardborder p-6 rounded-2xl max-w-5xl w-full my-8 space-y-6 shadow-2xl relative max-h-[90vh] flex flex-col">
            <!-- Modal Header -->
            <div class="flex items-center justify-between border-b border-cardborder pb-4 shrink-0">
                <div class="flex items-center space-x-3">
                    <div class="w-12 h-12 rounded-xl bg-gradient-to-tr from-accentblue to-indigo-600 flex items-center justify-center font-bold text-2xl text-white shadow-lg" id="ud-avatar">
                        U
                    </div>
                    <div>
                        <div class="flex items-center space-x-2">
                            <h3 class="text-xl font-bold text-white" id="ud-username">Nutzer Details</h3>
                            <span id="ud-status-badge" class="px-2.5 py-0.5 text-[10px] font-bold rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">Aktiv</span>
                        </div>
                        <p class="text-xs text-slate-400" id="ud-user-subtitle">PIN: - | ID: #0 | Registriert: -</p>
                    </div>
                </div>
                <button onclick="closeUserDetailsModal()" class="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300">
                    <i data-lucide="x" class="w-5 h-5"></i>
                </button>
            </div>

            <!-- Scrollable Body Container -->
            <div class="overflow-y-auto space-y-6 flex-1 pr-1">
                <!-- Quick KPI Stats Cards -->
                <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                    <div class="bg-slate-900/80 border border-cardborder p-3 rounded-xl">
                        <p class="text-[10px] uppercase font-semibold text-slate-400">Gesamt-Wiedergabe</p>
                        <p class="text-base font-bold text-amber-400 mt-1" id="ud-stat-watchtime">0 Std.</p>
                    </div>
                    <div class="bg-slate-900/80 border border-cardborder p-3 rounded-xl">
                        <p class="text-[10px] uppercase font-semibold text-slate-400">Gesehene Folgen</p>
                        <p class="text-base font-bold text-emerald-400 mt-1" id="ud-stat-completed">0 / 0</p>
                    </div>
                    <div class="bg-slate-900/80 border border-cardborder p-3 rounded-xl">
                        <p class="text-[10px] uppercase font-semibold text-slate-400">Fertigstellungsquote</p>
                        <p class="text-base font-bold text-blue-400 mt-1" id="ud-stat-rate">0%</p>
                    </div>
                    <div class="bg-slate-900/80 border border-cardborder p-3 rounded-xl">
                        <p class="text-[10px] uppercase font-semibold text-slate-400">In Merkliste</p>
                        <p class="text-base font-bold text-rose-400 mt-1" id="ud-stat-watchlist">0</p>
                    </div>
                    <div class="bg-slate-900/80 border border-cardborder p-3 rounded-xl">
                        <p class="text-[10px] uppercase font-semibold text-slate-400">Suchanfragen</p>
                        <p class="text-base font-bold text-purple-400 mt-1" id="ud-stat-searches">0</p>
                    </div>
                    <div class="bg-slate-900/80 border border-cardborder p-3 rounded-xl">
                        <p class="text-[10px] uppercase font-semibold text-slate-400">Haupt-Quelle</p>
                        <p class="text-base font-bold text-cyan-400 mt-1" id="ud-stat-source">-</p>
                    </div>
                </div>

                <!-- Charts Row -->
                <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
                    <!-- Daily Activity Chart -->
                    <div class="lg:col-span-2 bg-slate-900/60 border border-cardborder p-4 rounded-xl space-y-2">
                        <h4 class="text-xs font-semibold text-slate-300 flex items-center space-x-1.5">
                            <i data-lucide="line-chart" class="w-4 h-4 text-accentblue"></i>
                            <span>Wiedergabedauer (Letzte 14 Tage)</span>
                        </h4>
                        <div class="h-44 relative">
                            <canvas id="userTimelineChart"></canvas>
                        </div>
                    </div>

                    <!-- Source Breakdown Doughnut -->
                    <div class="bg-slate-900/60 border border-cardborder p-4 rounded-xl space-y-2 flex flex-col justify-between">
                        <h4 class="text-xs font-semibold text-slate-300 flex items-center space-x-1.5">
                            <i data-lucide="pie-chart" class="w-4 h-4 text-amber-400"></i>
                            <span>Quellen-Nutzung</span>
                        </h4>
                        <div class="h-36 relative flex items-center justify-center">
                            <canvas id="userSourceChart"></canvas>
                        </div>
                    </div>
                </div>

                <!-- Detail Tabs -->
                <div class="space-y-4">
                    <div class="flex space-x-2 border-b border-cardborder pb-2">
                        <button onclick="setUserDetailTab('history')" id="udtab-btn-history" class="text-xs font-bold px-3.5 py-2 rounded-xl bg-accentred text-white shadow">
                            Wiedergabeverlauf
                        </button>
                        <button onclick="setUserDetailTab('watchlist')" id="udtab-btn-watchlist" class="text-xs font-bold px-3.5 py-2 rounded-xl text-slate-400 hover:text-white">
                            Merkliste
                        </button>
                        <button onclick="setUserDetailTab('searches')" id="udtab-btn-searches" class="text-xs font-bold px-3.5 py-2 rounded-xl text-slate-400 hover:text-white">
                            Suchverlauf
                        </button>
                    </div>

                    <!-- Sub-tab 1: Wiedergabeverlauf -->
                    <div id="udtab-content-history" class="space-y-3">
                        <div class="overflow-x-auto border border-cardborder rounded-xl">
                            <table class="w-full text-left text-xs text-slate-300">
                                <thead class="bg-slate-950 text-slate-400 uppercase font-semibold">
                                    <tr>
                                        <th class="p-2.5">Zeitpunkt</th>
                                        <th class="p-2.5">Serie / Film</th>
                                        <th class="p-2.5">Folge / Titel</th>
                                        <th class="p-2.5">Fortschritt</th>
                                        <th class="p-2.5 text-center">Status</th>
                                        <th class="p-2.5 text-right">Quelle</th>
                                    </tr>
                                </thead>
                                <tbody id="ud-history-tbody" class="divide-y divide-slate-800">
                                    <tr><td colspan="6" class="p-4 text-center text-slate-500">Lade Verlauf...</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- Sub-tab 2: Merkliste -->
                    <div id="udtab-content-watchlist" class="space-y-3 hidden">
                        <div id="ud-watchlist-grid" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                            <div class="text-xs text-slate-500 col-span-full p-4 text-center">Lade Merkliste...</div>
                        </div>
                    </div>

                    <!-- Sub-tab 3: Suchverlauf -->
                    <div id="udtab-content-searches" class="space-y-3 hidden">
                        <div class="overflow-x-auto border border-cardborder rounded-xl">
                            <table class="w-full text-left text-xs text-slate-300">
                                <thead class="bg-slate-950 text-slate-400 uppercase font-semibold">
                                    <tr>
                                        <th class="p-2.5">Zeitpunkt</th>
                                        <th class="p-2.5">Suchbegriff</th>
                                        <th class="p-2.5">Genre Filter</th>
                                        <th class="p-2.5 text-center">Ergebnisse</th>
                                        <th class="p-2.5 text-right">Quelle</th>
                                    </tr>
                                </thead>
                                <tbody id="ud-searches-tbody" class="divide-y divide-slate-800">
                                    <tr><td colspan="5" class="p-4 text-center text-slate-500">Lade Suchverlauf...</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Create User Modal -->
    <div id="create-user-modal" class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm hidden flex items-center justify-center p-4 z-50">
        <div class="bg-cardbg border border-cardborder p-6 rounded-2xl max-w-md w-full space-y-4 shadow-2xl">
            <h3 class="text-lg font-bold text-white flex items-center space-x-2">
                <i data-lucide="user-plus" class="w-5 h-5 text-accentred"></i>
                <span>Neuen Benutzer anlegen</span>
            </h3>

            <div class="space-y-3 text-xs">
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Benutzername</label>
                    <input type="text" id="new-user-name" placeholder="z.B. Alex" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-accentred">
                </div>
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Anmeldecode / PIN (Optional, sonst automatisch)</label>
                    <input type="text" id="new-user-code" placeholder="z.B. 123456" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-accentred">
                </div>
            </div>

            <div class="flex justify-end space-x-2 pt-2">
                <button onclick="closeCreateUserModal()" class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl">Abbrechen</button>
                <button onclick="submitCreateUser()" class="px-4 py-2 bg-accentred hover:bg-rose-700 text-white text-xs font-bold rounded-xl shadow">Erstellen</button>
            </div>
        </div>
    </div>

    <!-- Edit User Modal -->
    <div id="edit-user-modal" class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm hidden flex items-center justify-center p-4 z-50">
        <div class="bg-cardbg border border-cardborder p-6 rounded-2xl max-w-md w-full space-y-4 shadow-2xl">
            <h3 class="text-lg font-bold text-white flex items-center space-x-2">
                <i data-lucide="user-cog" class="w-5 h-5 text-amber-400"></i>
                <span>Benutzer bearbeiten</span>
            </h3>

            <input type="hidden" id="edit-user-id">

            <div class="space-y-3 text-xs">
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Benutzername</label>
                    <input type="text" id="edit-user-name" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-amber-400">
                </div>
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Anmeldecode / PIN</label>
                    <input type="text" id="edit-user-code" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-amber-400">
                </div>
                <div class="flex items-center space-x-2 pt-1">
                    <input type="checkbox" id="edit-user-active" class="w-4 h-4 rounded bg-slate-900 border-cardborder text-accentred focus:ring-0">
                    <label for="edit-user-active" class="text-slate-300 font-medium">Konto ist aktiv</label>
                </div>
            </div>

            <div class="flex justify-end space-x-2 pt-2">
                <button onclick="closeEditUserModal()" class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl">Abbrechen</button>
                <button onclick="submitEditUser()" class="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-slate-950 text-xs font-bold rounded-xl shadow">Speichern</button>
            </div>
        </div>
    </div>

    <!-- Manual Ban Modal -->
    <div id="manual-ban-modal" class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm hidden flex items-center justify-center p-4 z-50">
        <div class="bg-cardbg border border-cardborder p-6 rounded-2xl max-w-md w-full space-y-4 shadow-2xl">
            <h3 class="text-lg font-bold text-white flex items-center space-x-2">
                <i data-lucide="shield-alert" class="w-5 h-5 text-rose-500"></i>
                <span>Gerät / IP manuell sperren</span>
            </h3>

            <div class="space-y-3 text-xs">
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">IP-Adresse</label>
                    <input type="text" id="ban-ip-address" placeholder="z.B. 192.168.2.100" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-rose-500">
                </div>
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Geräte-ID (Optional)</label>
                    <input type="text" id="ban-device-id" placeholder="z.B. device_12345" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-rose-500">
                </div>
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Gerätename / Beschreibung</label>
                    <input type="text" id="ban-device-name" placeholder="z.B. Unbekannter TV" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-rose-500">
                </div>
                <div>
                    <label class="block text-slate-400 mb-1 font-medium">Grund für Sperre</label>
                    <input type="text" id="ban-reason" placeholder="z.B. Verdächtiger Zugriff" class="w-full bg-slate-900 border border-cardborder text-white p-2.5 rounded-xl outline-none focus:border-rose-500">
                </div>
            </div>

            <div class="flex justify-end space-x-2 pt-2">
                <button onclick="closeManualBanModal()" class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl">Abbrechen</button>
                <button onclick="submitManualBan()" class="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold rounded-xl shadow">Sperren</button>
            </div>
        </div>
    </div>

    <!-- Scripts -->
    <script>
        let currentTab = 'dashboard';
        let timelineChart = null;
        let sourceChart = null;
        let logPage = 1;
        let currentDb = 'users';
        let currentTable = '';
        let dbPage = 1;
        let dbSubTab = 'data';

        function switchTab(tab) {
            currentTab = tab;
            document.querySelectorAll('.nav-tab').forEach(btn => {
                btn.classList.remove('bg-accentred', 'text-white', 'shadow');
                btn.classList.add('text-slate-400');
            });

            const activeBtn = document.getElementById(`tab-btn-${tab}`);
            if (activeBtn) {
                activeBtn.classList.add('bg-accentred', 'text-white', 'shadow');
                activeBtn.classList.remove('text-slate-400');
            }

            document.querySelectorAll('[id^="tab-content-"]').forEach(c => c.classList.add('hidden'));
            document.getElementById(`tab-content-${tab}`).classList.remove('hidden');

            if (tab === 'dashboard') loadDashboardData();
            if (tab === 'activity') loadActivityLogs();
            if (tab === 'users') loadUsers();
            if (tab === 'database') loadDatabaseTables();
            if (tab === 'banned') loadBannedDevices();
            if (tab === 'scraper') startLogPolling(); else stopLogPolling();
            lucide.createIcons();
        }

        function debounce(func, wait) {
            let timeout;
            return function(...args) {
                clearTimeout(timeout);
                timeout = setTimeout(() => func.apply(this, args), wait);
            };
        }

        // --- TAB 1: DASHBOARD ---
        async function loadDashboardData() {
            try {
                const [statsRes, topRes, chartsRes] = await Promise.all([
                    fetch('/admin/api/stats').then(r => r.json()),
                    fetch('/admin/api/top-content').then(r => r.json()),
                    fetch('/admin/api/charts-data').then(r => r.json())
                ]);

                // Update Cards
                document.getElementById('stat-users-count').innerText = `${statsRes.total_users} (Aktiv: ${statsRes.active_users})`;
                document.getElementById('stat-active-sessions').innerText = `${statsRes.active_sessions} Aktive Gerätesitzungen`;

                document.getElementById('stat-watch-hours').innerText = `${statsRes.total_watch_hours} Std.`;
                document.getElementById('stat-watch-events').innerText = `${statsRes.total_watch_events} Fortgabe-Einträge`;

                const totalTitles = Object.values(statsRes.content_counts).reduce((a, b) => a + b, 0);
                document.getElementById('stat-content-total').innerText = totalTitles;
                document.getElementById('stat-source-counts').innerText = `AniWorld (${statsRes.content_counts.aniworld || 0}) | S.to (${statsRes.content_counts.sto || 0}) | Filmo (${statsRes.content_counts.filmo || 0})`;

                document.getElementById('stat-completed-count').innerText = statsRes.completed_episodes;
                const totalEps = statsRes.completed_episodes + statsRes.in_progress_episodes;
                const rate = totalEps > 0 ? Math.round((statsRes.completed_episodes / totalEps) * 100) : 0;
                document.getElementById('stat-completion-rate').innerText = `${rate}% Fertigstellungsquote`;

                // Render Top Content Table
                const topBody = document.getElementById('top-content-tbody');
                if (topRes.length === 0) {
                    topBody.innerHTML = '<tr><td colspan="6" class="p-4 text-center text-slate-500">Noch keine Wiedergabe-Daten vorhanden.</td></tr>';
                } else {
                    topBody.innerHTML = topRes.map(item => `
                        <tr class="hover:bg-slate-800/50 transition">
                            <td class="p-3 flex items-center space-x-3 font-semibold text-white">
                                <img src="${item.cover_url || 'https://via.placeholder.com/40x60'}" class="w-8 h-12 object-cover rounded shadow">
                                <span>${item.title}</span>
                            </td>
                            <td class="p-3"><span class="px-2 py-0.5 text-[10px] uppercase font-bold rounded bg-slate-800 border border-cardborder">${item.source}</span></td>
                            <td class="p-3 text-xs text-slate-400">${item.genre || '-'}</td>
                            <td class="p-3 text-center font-bold text-accentblue">${item.unique_viewers}</td>
                            <td class="p-3 text-center text-slate-300">${item.total_views}</td>
                            <td class="p-3 text-right font-bold text-amber-400">${item.total_watch_hours} h</td>
                        </tr>
                    `).join('');
                }

                // Render Timeline Chart
                renderTimelineChart(chartsRes.daily_activity);
                renderSourceChart(chartsRes.source_breakdown);

            } catch (err) {
                console.error("Fehler beim Laden der Dashboard-Daten:", err);
            }
        }

        function renderTimelineChart(daily) {
            const ctx = document.getElementById('timelineChart').getContext('2d');
            if (timelineChart) timelineChart.destroy();

            timelineChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: daily.labels.length ? daily.labels : ['Heute'],
                    datasets: [{
                        label: 'Wiedergabedauer (Stunden)',
                        data: daily.hours.length ? daily.hours : [0],
                        borderColor: '#3b82f6',
                        backgroundColor: 'rgba(59, 130, 246, 0.15)',
                        fill: true,
                        tension: 0.3
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        x: { grid: { color: '#334155' }, ticks: { color: '#94a3b8' } },
                        y: { grid: { color: '#334155' }, ticks: { color: '#94a3b8' } }
                    }
                }
            });
        }

        function renderSourceChart(sources) {
            const ctx = document.getElementById('sourceChart').getContext('2d');
            if (sourceChart) sourceChart.destroy();

            const labels = Object.keys(sources);
            const data = Object.values(sources).map(s => s.count);

            sourceChart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: labels.length ? labels : ['Keine Daten'],
                    datasets: [{
                        data: data.length && data.some(v => v > 0) ? data : [1],
                        backgroundColor: ['#e11d48', '#f59e0b', '#3b82f6', '#10b981']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { position: 'bottom', labels: { color: '#94a3b8', font: { size: 10 } } } }
                }
            });
        }

        // --- TAB 2: ACTIVITY LOGS ---
        async function loadActivityLogs() {
            const src = document.getElementById('log-filter-source').value;
            const search = document.getElementById('log-filter-search').value;

            try {
                const res = await fetch(`/admin/api/watch-logs?source=${src}&search=${search}&page=${logPage}&limit=30`).then(r => r.json());
                const tbody = document.getElementById('activity-logs-tbody');

                if (!res.logs || res.logs.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" class="p-4 text-center text-slate-500">Keine Protokoll-Einträge gefunden.</td></tr>';
                } else {
                    tbody.innerHTML = res.logs.map(log => {
                        const dateStr = log.timestamp ? new Date(log.timestamp * 1000).toLocaleString('de-DE') : '-';
                        const duration = log.duration_seconds || 1;
                        const pct = Math.min(100, Math.round(((log.progress_seconds || 0) / duration) * 100));

                        return `
                            <tr class="hover:bg-slate-800/50 transition">
                                <td class="p-3 text-xs text-slate-400 font-mono">${dateStr}</td>
                                <td class="p-3 font-semibold text-white flex items-center space-x-2">
                                    <div class="w-6 h-6 rounded-full bg-slate-800 text-slate-300 text-xs flex items-center justify-center font-bold">
                                        ${(log.username || 'U')[0].toUpperCase()}
                                    </div>
                                    <span>${log.username || 'Gast'}</span>
                                </td>
                                <td class="p-3 font-semibold text-slate-200">
                                    <div class="flex items-center space-x-2">
                                        ${log.cover_url ? `<img src="${log.cover_url}" class="w-6 h-9 object-cover rounded">` : ''}
                                        <span>${log.series_title || 'Unbekannt'}</span>
                                    </div>
                                </td>
                                <td class="p-3 text-xs text-slate-300">
                                    <span class="font-bold text-amber-400">S${log.season_number}E${log.episode_number}</span> - ${log.episode_title}
                                </td>
                                <td class="p-3 w-40">
                                    <div class="flex items-center space-x-2">
                                        <div class="flex-1 bg-slate-900 rounded-full h-2 overflow-hidden border border-cardborder">
                                            <div class="bg-gradient-to-r from-amber-500 to-rose-500 h-full" style="width: ${pct}%"></div>
                                        </div>
                                        <span class="text-[10px] font-bold text-slate-400">${pct}%</span>
                                    </div>
                                </td>
                                <td class="p-3 text-right">
                                    <span class="px-2 py-0.5 text-[10px] uppercase font-bold rounded bg-slate-800 border border-cardborder text-slate-300">${log.source}</span>
                                </td>
                            </tr>
                        `;
                    }).join('');
                }

                document.getElementById('log-page-info').innerText = `Seite ${res.page} von ${Math.ceil(res.total / res.limit) || 1} (${res.total} Gesamteinträge)`;
                document.getElementById('btn-log-prev').disabled = logPage <= 1;
                document.getElementById('btn-log-next').disabled = logPage * res.limit >= res.total;

            } catch (err) {
                console.error("Fehler beim Laden der Logs:", err);
            }
        }

        function changeLogPage(delta) {
            logPage += delta;
            if (logPage < 1) logPage = 1;
            loadActivityLogs();
        }

        // --- TAB 3: USERS ---
        async function loadUsers() {
            try {
                const users = await fetch('/admin/api/users').then(r => r.json());
                const tbody = document.getElementById('users-tbody');

                tbody.innerHTML = users.map(u => {
                    const code = u.login_code || u.password || '';
                    return `
                        <tr class="hover:bg-slate-800/50 transition">
                            <td class="p-3 font-mono text-xs text-slate-500">#${u.id}</td>
                            <td class="p-3 font-bold text-white">${u.username}</td>
                            <td class="p-3 font-mono text-xs text-emerald-400">${code || '-'}</td>
                            <td class="p-3">
                                ${u.is_active ?
                                    '<span class="px-2.5 py-1 text-[10px] font-bold rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">Aktiv</span>' :
                                    '<span class="px-2.5 py-1 text-[10px] font-bold rounded-full bg-rose-500/20 text-rose-400 border border-rose-500/30">Gesperrt</span>'
                                }
                            </td>
                            <td class="p-3 text-xs text-slate-400 font-mono">${u.device_id ? `${u.device_name || 'Gerät'} (${u.device_id})` : 'Keine aktive Sitzung'}</td>
                            <td class="p-3 text-center font-bold text-accentblue">${u.watch_count || 0}</td>
                            <td class="p-3 text-right space-x-2">
                            <td class="p-3 text-right space-x-2">
                                <button onclick="openUserDetailsModal(${u.id})" class="px-3 py-1 text-xs rounded-lg border border-cardborder bg-accentblue/20 hover:bg-accentblue/30 text-blue-300 font-semibold inline-flex items-center space-x-1">
                                    <i data-lucide="bar-chart-2" class="w-3.5 h-3.5 mr-1"></i>
                                    <span>Details</span>
                                </button>
                                <button onclick="openEditUserModal(${u.id}, '${u.username.replace(/'/g, "\\'")}', '${code.replace(/'/g, "\\'")}', ${u.is_active ? 'true' : 'false'})" class="px-3 py-1 text-xs rounded-lg border border-cardborder bg-amber-500/10 hover:bg-amber-500/20 text-amber-300">
                                    Bearbeiten
                                </button>
                                <button onclick="toggleUserStatus(${u.id})" class="px-3 py-1 text-xs rounded-lg border border-cardborder bg-slate-800 hover:bg-slate-700 text-slate-200">
                                    ${u.is_active ? 'Sperren' : 'Freischalten'}
                                </button>
                                <button onclick="deleteUser(${u.id}, '${u.username.replace(/'/g, "\\'")}')" class="px-3 py-1 text-xs rounded-lg border border-cardborder bg-rose-950/40 hover:bg-rose-900/60 text-rose-300">
                                    Löschen
                                </button>
                            </td>
                        </tr>
                    `;
                }).join('');
            } catch (err) {
                console.error("Fehler beim Laden der Benutzer:", err);
            }
        }

        // --- USER DETAILS MODAL JS ---
        let currentDetailUserId = null;
        let userTimelineChart = null;
        let userSourceChart = null;

        async function openUserDetailsModal(userId) {
            currentDetailUserId = userId;
            document.getElementById('user-details-modal').classList.remove('hidden');
            setUserDetailTab('history');

            try {
                const res = await fetch(`/admin/api/users/${userId}/details`).then(r => r.json());
                if (res.status === 'error') return alert(res.message);

                const u = res.user;
                const s = res.stats;

                document.getElementById('ud-username').innerText = u.username;
                document.getElementById('ud-avatar').innerText = u.username[0].toUpperCase();
                document.getElementById('ud-status-badge').className = u.is_active ?
                    'px-2.5 py-0.5 text-[10px] font-bold rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' :
                    'px-2.5 py-0.5 text-[10px] font-bold rounded-full bg-rose-500/20 text-rose-400 border border-rose-500/30';
                document.getElementById('ud-status-badge').innerText = u.is_active ? 'Aktiv' : 'Gesperrt';

                const createdStr = u.created_at ? new Date(u.created_at * 1000).toLocaleDateString('de-DE') : '-';
                document.getElementById('ud-user-subtitle').innerText = `PIN: ${u.login_code || u.password || '-'} | ID: #${u.id} | Registriert: ${createdStr}`;

                // KPI Stats
                document.getElementById('ud-stat-watchtime').innerText = s.total_watch_minutes_str;
                document.getElementById('ud-stat-completed').innerText = `${s.completed_episodes} / ${s.total_records}`;
                document.getElementById('ud-stat-rate').innerText = `${s.completion_rate}%`;
                document.getElementById('ud-stat-watchlist').innerText = s.watchlist_count;
                document.getElementById('ud-stat-searches').innerText = s.search_count;
                document.getElementById('ud-stat-source').innerText = s.favorite_source;

                // Render User Charts
                renderUserTimelineChart(s.daily_activity);
                renderUserSourceChart(s.sources_breakdown);

                // Load Subtab contents
                loadUserWatchHistory();
                loadUserWatchlist();
                loadUserSearchHistory();

                lucide.createIcons();

            } catch (err) {
                console.error("Fehler beim Laden der Nutzerdetails:", err);
            }
        }

        function closeUserDetailsModal() {
            document.getElementById('user-details-modal').classList.add('hidden');
        }

        function setUserDetailTab(tab) {
            ['history', 'watchlist', 'searches'].forEach(t => {
                const btn = document.getElementById(`udtab-btn-${t}`);
                const content = document.getElementById(`udtab-content-${t}`);
                if (btn && content) {
                    if (t === tab) {
                        btn.className = 'text-xs font-bold px-3.5 py-2 rounded-xl bg-accentred text-white shadow';
                        content.classList.remove('hidden');
                    } else {
                        btn.className = 'text-xs font-bold px-3.5 py-2 rounded-xl text-slate-400 hover:text-white';
                        content.classList.add('hidden');
                    }
                }
            });
        }

        function renderUserTimelineChart(daily) {
            const ctx = document.getElementById('userTimelineChart').getContext('2d');
            if (userTimelineChart) userTimelineChart.destroy();

            userTimelineChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: daily.labels.length ? daily.labels : ['Keine Daten'],
                    datasets: [{
                        label: 'Wiedergabedauer (Stunden)',
                        data: daily.hours.length ? daily.hours : [0],
                        borderColor: '#e11d48',
                        backgroundColor: 'rgba(225, 29, 72, 0.15)',
                        fill: true,
                        tension: 0.3
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        x: { grid: { color: '#334155' }, ticks: { color: '#94a3b8', font: { size: 10 } } },
                        y: { grid: { color: '#334155' }, ticks: { color: '#94a3b8', font: { size: 10 } } }
                    }
                }
            });
        }

        function renderUserSourceChart(sources) {
            const ctx = document.getElementById('userSourceChart').getContext('2d');
            if (userSourceChart) userSourceChart.destroy();

            const labels = Object.keys(sources).map(k => k.toUpperCase());
            const data = Object.values(sources).map(s => s.count);

            userSourceChart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: labels.length ? labels : ['Keine Daten'],
                    datasets: [{
                        data: data.length && data.some(v => v > 0) ? data : [1],
                        backgroundColor: ['#e11d48', '#f59e0b', '#3b82f6', '#10b981']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { position: 'right', labels: { color: '#94a3b8', font: { size: 10 } } } }
                }
            });
        }

        async function loadUserWatchHistory() {
            if (!currentDetailUserId) return;
            try {
                const res = await fetch(`/admin/api/users/${currentDetailUserId}/watch-logs?limit=50`).then(r => r.json());
                const tbody = document.getElementById('ud-history-tbody');

                if (!res.logs || res.logs.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" class="p-4 text-center text-slate-500">Noch keine Inhalte angeschaut.</td></tr>';
                    return;
                }

                tbody.innerHTML = res.logs.map(log => {
                    const dateStr = log.timestamp ? new Date(log.timestamp * 1000).toLocaleString('de-DE') : '-';
                    const duration = log.duration_seconds || 1;
                    const prog = log.progress_seconds || 0;
                    const pct = Math.min(100, Math.round((prog / duration) * 100));

                    const formatTime = (sec) => {
                        const m = Math.floor(sec / 60);
                        const s = Math.floor(sec % 60);
                        return `${m}:${s < 10 ? '0' : ''}${s}`;
                    };

                    return `
                        <tr class="hover:bg-slate-800/50 transition">
                            <td class="p-2.5 text-slate-400 font-mono text-[11px]">${dateStr}</td>
                            <td class="p-2.5 font-bold text-white flex items-center space-x-2">
                                ${log.cover_url ? `<img src="${log.cover_url}" class="w-6 h-9 object-cover rounded shadow">` : ''}
                                <span>${log.series_title || 'Serie #' + log.content_id}</span>
                            </td>
                            <td class="p-2.5 text-slate-300">
                                <span class="font-bold text-amber-400">S${log.season_number}E${log.episode_number}</span> - ${log.episode_title}
                            </td>
                            <td class="p-2.5 w-36">
                                <div class="space-y-1">
                                    <div class="bg-slate-900 rounded-full h-1.5 overflow-hidden border border-cardborder">
                                        <div class="bg-gradient-to-r from-amber-500 to-rose-500 h-full" style="width: ${pct}%"></div>
                                    </div>
                                    <div class="flex justify-between text-[10px] text-slate-400 font-mono">
                                        <span>${formatTime(prog)} / ${formatTime(duration)}</span>
                                        <span>${pct}%</span>
                                    </div>
                                </div>
                            </td>
                            <td class="p-2.5 text-center">
                                ${log.is_completed ?
                                    '<span class="px-2 py-0.5 text-[10px] font-bold rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">Fertig</span>' :
                                    '<span class="px-2 py-0.5 text-[10px] font-bold rounded bg-amber-500/20 text-amber-400 border border-amber-500/30">Läuft</span>'
                                }
                            </td>
                            <td class="p-2.5 text-right font-mono text-[10px] uppercase text-slate-400">${log.source}</td>
                        </tr>
                    `;
                }).join('');
            } catch (e) {
                console.error(e);
            }
        }

        async function loadUserWatchlist() {
            if (!currentDetailUserId) return;
            try {
                const res = await fetch(`/admin/api/users/${currentDetailUserId}/watchlist`).then(r => r.json());
                const grid = document.getElementById('ud-watchlist-grid');

                if (!res || res.length === 0) {
                    grid.innerHTML = '<div class="text-xs text-slate-500 col-span-full p-4 text-center">Keine Titel in der Merkliste.</div>';
                    return;
                }

                grid.innerHTML = res.map(item => {
                    const addedDate = item.added_at ? new Date(item.added_at * 1000).toLocaleDateString('de-DE') : '-';
                    return `
                        <div class="bg-slate-900 border border-cardborder rounded-xl p-3 flex items-center space-x-3 shadow">
                            <img src="${item.cover_url || 'https://via.placeholder.com/60x90'}" class="w-12 h-16 object-cover rounded shadow shrink-0">
                            <div class="min-w-0 flex-1">
                                <h5 class="text-xs font-bold text-white truncate" title="${item.title}">${item.title}</h5>
                                <p class="text-[10px] text-amber-400 mt-0.5 truncate">${item.genre || 'Kein Genre'}</p>
                                <div class="flex items-center justify-between text-[10px] text-slate-400 mt-2">
                                    <span class="uppercase font-bold text-slate-500">${item.source}</span>
                                    <span>+ ${addedDate}</span>
                                </div>
                            </div>
                        </div>
                    `;
                }).join('');
            } catch (e) {
                console.error(e);
            }
        }

        async function loadUserSearchHistory() {
            if (!currentDetailUserId) return;
            try {
                const res = await fetch(`/admin/api/users/${currentDetailUserId}/searches`).then(r => r.json());
                const tbody = document.getElementById('ud-searches-tbody');

                if (!res.searches || res.searches.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5" class="p-4 text-center text-slate-500">Noch keine Suchanfragen protokolliert.</td></tr>';
                    return;
                }

                tbody.innerHTML = res.searches.map(s => {
                    const dateStr = s.timestamp ? new Date(s.timestamp * 1000).toLocaleString('de-DE') : '-';
                    return `
                        <tr class="hover:bg-slate-800/50 transition font-mono">
                            <td class="p-2.5 text-slate-400 text-[11px]">${dateStr}</td>
                            <td class="p-2.5 font-bold text-amber-400">"${s.query || '-'}"</td>
                            <td class="p-2.5 text-slate-300 text-xs">${s.genre || 'Alle Genres'}</td>
                            <td class="p-2.5 text-center font-bold text-blue-400 text-xs">${s.results_count}</td>
                            <td class="p-2.5 text-right text-[10px] uppercase text-slate-400">${s.source}</td>
                        </tr>
                    `;
                }).join('');
            } catch (e) {
                console.error(e);
            }
        }

        async function toggleUserStatus(userId) {
            await fetch('/admin/api/users/toggle-status', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ user_id: userId })
            });
            loadUsers();
        }

        async function resetUserSession(userId) {
            await fetch('/admin/api/users/reset-session', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ user_id: userId })
            });
            loadUsers();
        }

        function openCreateUserModal() { document.getElementById('create-user-modal').classList.remove('hidden'); }
        function closeCreateUserModal() { document.getElementById('create-user-modal').classList.add('hidden'); }

        async function submitCreateUser() {
            const username = document.getElementById('new-user-name').value.trim();
            const code = document.getElementById('new-user-code').value.trim();
            if (!username) return alert('Benutzername erforderlich!');

            const res = await fetch('/admin/users', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, login_code: code || null })
            });

            if (res.ok) {
                closeCreateUserModal();
                loadUsers();
            } else {
                const err = await res.json();
                alert('Fehler: ' + (err.detail || 'Konnte Benutzer nicht anlegen.'));
            }
        }

        function openEditUserModal(id, username, code, isActive) {
            document.getElementById('edit-user-id').value = id;
            document.getElementById('edit-user-name').value = username;
            document.getElementById('edit-user-code').value = code;
            document.getElementById('edit-user-active').checked = isActive;
            document.getElementById('edit-user-modal').classList.remove('hidden');
        }

        function closeEditUserModal() {
            document.getElementById('edit-user-modal').classList.add('hidden');
        }

        async function submitEditUser() {
            const userId = parseInt(document.getElementById('edit-user-id').value);
            const username = document.getElementById('edit-user-name').value.trim();
            const code = document.getElementById('edit-user-code').value.trim();
            const isActive = document.getElementById('edit-user-active').checked;

            if (!username || !code) return alert('Benutzername und Anmeldecode erforderlich!');

            const res = await fetch(`/admin/users/${userId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ user_id: userId, username, login_code: code, is_active: isActive })
            });

            if (res.ok) {
                closeEditUserModal();
                loadUsers();
            } else {
                const err = await res.json();
                alert('Fehler: ' + (err.detail || 'Konnte Benutzer nicht aktualisieren.'));
            }
        }

        async function deleteUser(userId, username) {
            if (!confirm(`Möchtest du den Benutzer '${username}' wirklich unwiderruflich löschen? Alle zugehörigen Daten (Fortschritt, Merkliste, Logs) werden entfernt.`)) return;

            const res = await fetch(`/admin/users/${userId}`, {
                method: 'DELETE'
            });

            if (res.ok) {
                loadUsers();
            } else {
                const err = await res.json();
                alert('Fehler: ' + (err.detail || 'Konnte Benutzer nicht löschen.'));
            }
        }

        // --- TAB 4: DATABASE EXPLORER ---
        async function loadDatabaseTables() {
            currentDb = document.getElementById('db-selector').value;
            try {
                const res = await fetch(`/admin/api/db/tables?db_name=${currentDb}`).then(r => r.json());
                const container = document.getElementById('db-tables-list');

                if (!res.tables || res.tables.length === 0) {
                    container.innerHTML = '<div class="text-xs text-slate-500">Keine Tabellen gefunden.</div>';
                    return;
                }

                container.innerHTML = res.tables.map(tbl => `
                    <button onclick="selectDatabaseTable('${tbl.name}')" class="w-full text-left p-2 rounded-lg text-xs font-semibold flex items-center justify-between hover:bg-slate-800 transition ${currentTable === tbl.name ? 'bg-accentblue/20 border border-accentblue/40 text-blue-300' : 'text-slate-300'}">
                        <span>${tbl.name}</span>
                        <span class="px-2 py-0.5 rounded-full bg-slate-950 text-[10px] text-slate-400 font-mono">${tbl.count}</span>
                    </button>
                `).join('');

                if (res.tables.length > 0 && (!currentTable || !res.tables.some(t => t.name === currentTable))) {
                    selectDatabaseTable(res.tables[0].name);
                }
            } catch (err) {
                console.error("Fehler beim Laden der DB Tabellen:", err);
            }
        }

        function selectDatabaseTable(tableName) {
            currentTable = tableName;
            dbPage = 1;
            document.getElementById('current-table-title').innerText = `Tabelle: ${tableName}`;
            loadDatabaseTables(); // refresh active highlight
            loadTableData();
        }

        function setDbSubTab(sub) {
            dbSubTab = sub;
            document.getElementById('dbtab-data').className = sub === 'data' ? 'text-xs font-semibold px-3 py-1.5 rounded-lg bg-slate-800 text-white' : 'text-xs font-semibold px-3 py-1.5 rounded-lg text-slate-400 hover:text-white';
            document.getElementById('dbtab-query').className = sub === 'query' ? 'text-xs font-semibold px-3 py-1.5 rounded-lg bg-slate-800 text-white' : 'text-xs font-semibold px-3 py-1.5 rounded-lg text-slate-400 hover:text-white';

            document.getElementById('dbsub-data').classList.toggle('hidden', sub !== 'data');
            document.getElementById('dbsub-query').classList.toggle('hidden', sub !== 'query');
        }

        async function loadTableData() {
            if (!currentTable) return;
            const search = document.getElementById('db-search-input').value;

            try {
                const res = await fetch(`/admin/api/db/data?db_name=${currentDb}&table_name=${currentTable}&page=${dbPage}&limit=30&search=${search}`).then(r => r.json());

                // Thead
                const thead = document.getElementById('db-table-thead');
                thead.innerHTML = `<tr>${res.columns.map(c => `<th class="p-3 border-b border-cardborder">${c}</th>`).join('')}</tr>`;

                // Tbody
                const tbody = document.getElementById('db-table-tbody');
                if (!res.rows || res.rows.length === 0) {
                    tbody.innerHTML = `<tr><td colspan="${res.columns.length || 1}" class="p-4 text-center text-slate-500">Keine Datensätze in dieser Tabelle.</td></tr>`;
                } else {
                    tbody.innerHTML = res.rows.map(row => `
                        <tr class="hover:bg-slate-800/50 font-mono text-[11px] text-slate-300">
                            ${res.columns.map(col => `<td class="p-3 border-b border-slate-800 max-w-xs truncate" title="${row[col]}">${row[col] !== null ? row[col] : '<span class="text-slate-600">NULL</span>'}</td>`).join('')}
                        </tr>
                    `).join('');
                }

                document.getElementById('db-page-info').innerText = `Seite ${res.page} von ${Math.ceil(res.total / res.limit) || 1} (${res.total} Zeilen)`;
                document.getElementById('btn-db-prev').disabled = dbPage <= 1;
                document.getElementById('btn-db-next').disabled = dbPage * res.limit >= res.total;

            } catch (err) {
                console.error("Fehler beim Laden der Tabellendaten:", err);
            }
        }

        function changeDbPage(delta) {
            dbPage += delta;
            if (dbPage < 1) dbPage = 1;
            loadTableData();
        }

        async function runSqlQuery() {
            const query = document.getElementById('sql-query-input').value.trim();
            if (!query) return;

            const res = await fetch('/admin/api/db/query', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ db_name: currentDb, query })
            }).then(r => r.json());

            const output = document.getElementById('sql-query-result');
            if (res.status === 'error') {
                output.innerHTML = `<span class="text-rose-400 font-bold">Fehler: ${res.message}</span>`;
            } else if (res.columns && res.rows) {
                output.innerHTML = `
                    <div class="mb-2 text-emerald-400 font-semibold">${res.row_count} Zeilen zurückgegeben:</div>
                    <table class="w-full text-left font-mono">
                        <thead class="bg-slate-950 text-slate-400">
                            <tr>${res.columns.map(c => `<th class="p-2 border-b border-cardborder">${c}</th>`).join('')}</tr>
                        </thead>
                        <tbody class="divide-y divide-slate-800">
                            ${res.rows.map(r => `<tr>${r.map(v => `<td class="p-2 truncate max-w-xs">${v}</td>`).join('')}</tr>`).join('')}
                        </tbody>
                    </table>
                `;
            } else {
                output.innerHTML = `<span class="text-emerald-400 font-bold">${res.message}</span>`;
            }
        }

        // --- TAB 5: SCRAPER & TERMINAL LOGS ---
        let lastLogId = 0;
        let logPollInterval = null;

        function formatLogLine(log) {
            let levelColor = "text-slate-300";
            if (log.level === "SUCCESS") levelColor = "text-emerald-400 font-semibold";
            else if (log.level === "ERROR") levelColor = "text-rose-400 font-bold";
            else if (log.level === "WARN") levelColor = "text-amber-400 font-semibold";

            let srcBadge = "bg-slate-800 text-slate-300";
            if (log.source === "ANIWORLD") srcBadge = "bg-blue-500/20 text-blue-400 border border-blue-500/30";
            else if (log.source === "STO") srcBadge = "bg-amber-500/20 text-amber-400 border border-amber-500/30";
            else if (log.source === "FILMO") srcBadge = "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30";
            else if (log.source === "SYSTEM") srcBadge = "bg-purple-500/20 text-purple-300 border border-purple-500/30";

            return `<div class="flex items-start space-x-2 py-0.5 hover:bg-slate-900/60 rounded px-1 transition-colors">
                <span class="text-slate-500 shrink-0 font-mono text-[11px]">[${log.timestamp}]</span>
                <span class="px-1.5 py-0.2 rounded text-[10px] font-bold uppercase tracking-wider shrink-0 ${srcBadge}">${log.source}</span>
                <span class="${levelColor} break-all">${escapeHtml(log.message)}</span>
            </div>`;
        }

        function escapeHtml(str) {
            return (str || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
        }

        async function pollIndexerLogs() {
            try {
                const res = await fetch(`/admin/api/indexer/logs?since_id=${lastLogId}`);
                if (!res.ok) return;
                const data = await res.json();

                const consoleEl = document.getElementById('terminal-console');
                const badgeEl = document.getElementById('indexer-status-badge');

                if (data.is_indexing || data.is_syncing) {
                    badgeEl.className = 'px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse';
                    badgeEl.innerText = data.is_indexing ? '● INDEXIERUNG LÄUFT' : '● SYNC LÄUFT';
                } else {
                    badgeEl.className = 'px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-slate-800 text-slate-400';
                    badgeEl.innerText = 'INAKTIV';
                }

                if (data.logs && data.logs.length > 0) {
                    if (lastLogId === 0 && consoleEl.querySelector('.italic')) {
                        consoleEl.innerHTML = '';
                    }

                    let htmlChunk = '';
                    for (const log of data.logs) {
                        htmlChunk += formatLogLine(log);
                        if (log.id > lastLogId) {
                            lastLogId = log.id;
                        }
                    }
                    consoleEl.innerHTML += htmlChunk;

                    const chk = document.getElementById('chk-autoscroll');
                    if (chk && chk.checked) {
                        consoleEl.scrollTop = consoleEl.scrollHeight;
                    }
                }
            } catch (e) {
                console.error('Fehler beim Abrufen der Terminal-Logs:', e);
            }
        }

        function startLogPolling() {
            if (logPollInterval) clearInterval(logPollInterval);
            pollIndexerLogs();
            logPollInterval = setInterval(pollIndexerLogs, 1000);
        }

        function stopLogPolling() {
            if (logPollInterval) {
                clearInterval(logPollInterval);
                logPollInterval = null;
            }
        }

        async function clearTerminalLogs() {
            try {
                await fetch('/admin/api/indexer/clear-logs', { method: 'POST' });
                lastLogId = 0;
                document.getElementById('terminal-console').innerHTML = '<div class="text-slate-500 italic">[SYSTEM] Terminal gelöscht.</div>';
            } catch(e) {
                console.error(e);
            }
        }

        async function triggerIndex(src) {
            startLogPolling();
            try {
                await fetch(`/admin/trigger-index?source=${src}`, { method: 'POST' });
            } catch(e) {
                console.error(e);
            }
        }

        async function triggerSchedulerNow() {
            startLogPolling();
            try {
                await fetch('/admin/scheduler/trigger', { method: 'POST' });
            } catch(e) {
                console.error(e);
            }
        }

        // --- TAB 6: GESPERRTE GERÄTE ---
        function openManualBanModal() {
            document.getElementById('ban-ip-address').value = '';
            document.getElementById('ban-device-id').value = '';
            document.getElementById('ban-device-name').value = '';
            document.getElementById('ban-reason').value = '';
            document.getElementById('manual-ban-modal').classList.remove('hidden');
        }

        function closeManualBanModal() {
            document.getElementById('manual-ban-modal').classList.add('hidden');
        }

        async function submitManualBan() {
            const ip = document.getElementById('ban-ip-address').value.trim();
            if (!ip) {
                alert("Bitte eine IP-Adresse eingeben");
                return;
            }
            const devId = document.getElementById('ban-device-id').value.trim();
            const devName = document.getElementById('ban-device-name').value.trim();
            const reason = document.getElementById('ban-reason').value.trim();

            try {
                const res = await fetch('/admin/api/banned-devices/ban', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        ip_address: ip,
                        device_id: devId || null,
                        device_name: devName || "Manuell gesperrt",
                        reason: reason || "Manuelle Sperre durch Administrator"
                    })
                }).then(r => r.json());

                if (res.status === 'success') {
                    closeManualBanModal();
                    loadBannedDevices();
                } else {
                    alert("Fehler: " + (res.message || "Sperren fehlgeschlagen"));
                }
            } catch (e) {
                alert("Fehler beim Sperren des Geräts: " + e);
            }
        }

        async function loadBannedDevices() {
            try {
                const list = await fetch('/admin/api/banned-devices').then(r => r.json());
                const tbody = document.getElementById('banned-devices-tbody');

                if (!list || list.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="9" class="p-6 text-center text-slate-500">Keine gesperrten Geräte vorhanden.</td></tr>';
                    return;
                }

                tbody.innerHTML = list.map(item => {
                    const bannedDate = item.banned_at ? new Date(item.banned_at * 1000).toLocaleString('de-DE') : '-';
                    const lastAttemptDate = item.last_attempt_at ? new Date(item.last_attempt_at * 1000).toLocaleString('de-DE') : '-';
                    const locationStr = escapeHtml(item.location || 'Unbekannt');
                    const devName = escapeHtml(item.device_name || 'Unbekanntes Gerät');
                    const devId = escapeHtml(item.device_id || '-');
                    const rawUa = item.user_agent || '';
                    const userAgent = escapeHtml(rawUa.length > 30 ? rawUa.substring(0, 30) + '...' : rawUa);

                    return `
                        <tr class="hover:bg-slate-800/50 transition border-b border-slate-800 text-xs">
                            <td class="p-3 font-mono font-bold text-rose-400">${escapeHtml(item.ip_address)}</td>
                            <td class="p-3 text-slate-300">
                                <span class="inline-flex items-center gap-1 text-slate-200 font-medium">
                                    <i data-lucide="map-pin" class="w-3.5 h-3.5 text-amber-400"></i>
                                    ${locationStr}
                                </span>
                            </td>
                            <td class="p-3">
                                <div class="font-semibold text-white">${devName}</div>
                                <div class="text-[10px] text-slate-400 font-mono">${devId}</div>
                            </td>
                            <td class="p-3 text-slate-400 text-[11px] font-mono" title="${escapeHtml(rawUa)}">${userAgent || '-'}</td>
                            <td class="p-3 text-center font-bold text-rose-500">${item.failed_attempts || 3}</td>
                            <td class="p-3 text-slate-300 whitespace-nowrap">${bannedDate}</td>
                            <td class="p-3 text-slate-400 whitespace-nowrap">${lastAttemptDate}</td>
                            <td class="p-3 text-slate-300">${escapeHtml(item.reason || '3 fehlgeschlagene Anmeldeversuche')}</td>
                            <td class="p-3 text-right">
                                <button onclick="unbanDevice(${item.id})" class="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-lg shadow transition flex items-center space-x-1 ml-auto">
                                    <i data-lucide="unlock" class="w-3.5 h-3.5"></i>
                                    <span>Entsperren</span>
                                </button>
                            </td>
                        </tr>
                    `;
                }).join('');

                lucide.createIcons();
            } catch (e) {
                console.error("Fehler beim Laden der gesperrten Geräte:", e);
            }
        }

        async function unbanDevice(id) {
            if (!confirm("Möchtest du dieses Gerät wirklich entsperren?")) return;

            try {
                const res = await fetch('/admin/api/banned-devices/unban', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id })
                }).then(r => r.json());

                if (res.status === 'success') {
                    loadBannedDevices();
                } else {
                    alert("Fehler beim Entsperren: " + (res.message || "Unbekannter Fehler"));
                }
            } catch (e) {
                alert("Fehler: " + e);
            }
        }

        // Init page
        document.addEventListener('DOMContentLoaded', () => {
            switchTab('dashboard');
        });
    </script>
</body>
</html>
"""
