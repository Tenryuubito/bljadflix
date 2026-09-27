import os
import re
import sqlite3
import threading
import time
from typing import List, Optional

SUPPORTED_SOURCES = ["aniworld", "sto", "filmo"]

class DatabaseManager:
    def __init__(self, db_dir: Optional[str] = None):
        if db_dir is None:
            db_dir = os.path.dirname(os.path.abspath(__file__))
        self.db_dir = db_dir
        self.lock = threading.Lock()

    def sanitize_source(self, source: Optional[str]) -> str:
        if not source:
            return "aniworld"
        clean = re.sub(r'[^a-zA-Z0-9_]', '', str(source)).lower()
        return clean if clean else "aniworld"

    def get_user_db_path(self) -> str:
        return os.path.join(self.db_dir, "users.db")

    def get_content_db_path(self, source: Optional[str] = "aniworld") -> str:
        clean_source = self.sanitize_source(source)
        return os.path.join(self.db_dir, f"movies_{clean_source}.db")

    def get_user_connection(self, row_factory: bool = False, timeout: float = 10.0) -> sqlite3.Connection:
        conn = sqlite3.connect(self.get_user_db_path(), check_same_thread=False, timeout=timeout)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        if row_factory:
            conn.row_factory = sqlite3.Row
        return conn

    def get_content_connection(self, source: Optional[str] = "aniworld", row_factory: bool = False, timeout: float = 10.0) -> sqlite3.Connection:
        db_path = self.get_content_db_path(source)
        conn = sqlite3.connect(db_path, check_same_thread=False, timeout=timeout)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        if row_factory:
            conn.row_factory = sqlite3.Row
        return conn

    # Legacy helper for single db calls - routes to default content db or user db as needed
    def get_connection(self, row_factory: bool = False, timeout: float = 10.0) -> sqlite3.Connection:
        return self.get_user_connection(row_factory=row_factory, timeout=timeout)

    def init_db(self, sources: Optional[List[str]] = None):
        if not sources:
            sources = SUPPORTED_SOURCES

        with self.lock:
            # 1. Initialize Users DB
            user_db_path = self.get_user_db_path()
            with sqlite3.connect(user_db_path) as u_conn:
                u_conn.execute("PRAGMA journal_mode=WAL;")
                cursor = u_conn.cursor()
                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        login_code TEXT NOT NULL UNIQUE,
                        password TEXT,
                        created_at REAL NOT NULL,
                        is_active BOOLEAN DEFAULT 1
                    )
                ''')
                
                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS user_sessions (
                        user_id INTEGER PRIMARY KEY,
                        token TEXT NOT NULL UNIQUE,
                        device_id TEXT NOT NULL,
                        device_name TEXT,
                        logged_in_at REAL NOT NULL,
                        last_active_at REAL NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users (id)
                    )
                ''')

                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS banned_devices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        device_id TEXT,
                        ip_address TEXT NOT NULL,
                        device_name TEXT,
                        user_agent TEXT,
                        location TEXT,
                        banned_at REAL NOT NULL,
                        failed_attempts INTEGER NOT NULL DEFAULT 3,
                        last_attempt_at REAL NOT NULL,
                        reason TEXT NOT NULL DEFAULT '3 fehlgeschlagene Anmeldeversuche'
                    )
                ''')

                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS login_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        device_id TEXT,
                        ip_address TEXT NOT NULL,
                        attempt_count INTEGER NOT NULL DEFAULT 1,
                        last_attempt_at REAL NOT NULL,
                        UNIQUE(device_id, ip_address)
                    )
                ''')

                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS watch_progress (
                        user_id INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'aniworld',
                        episode_id INTEGER NOT NULL,
                        content_id INTEGER NOT NULL,
                        progress_seconds REAL DEFAULT 0,
                        duration_seconds REAL DEFAULT 0,
                        is_completed BOOLEAN DEFAULT 0,
                        last_watched_at REAL,
                        PRIMARY KEY (user_id, source, episode_id),
                        FOREIGN KEY (user_id) REFERENCES users (id)
                    )
                ''')

                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS watchlist (
                        user_id INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'aniworld',
                        content_id INTEGER NOT NULL,
                        added_at REAL NOT NULL,
                        PRIMARY KEY (user_id, source, content_id),
                        FOREIGN KEY (user_id) REFERENCES users (id)
                    )
                ''')

                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS watch_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'aniworld',
                        content_id INTEGER NOT NULL,
                        episode_id INTEGER NOT NULL,
                        progress_seconds REAL DEFAULT 0,
                        duration_seconds REAL DEFAULT 0,
                        is_completed BOOLEAN DEFAULT 0,
                        timestamp REAL NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users (id)
                    )
                ''')

                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS search_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'aniworld',
                        query TEXT,
                        genre TEXT,
                        results_count INTEGER DEFAULT 0,
                        timestamp REAL NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users (id)
                    )
                ''')

                # Seed default users
                cursor.execute("SELECT COUNT(*) FROM users WHERE username = 'Admin'")
                if cursor.fetchone()[0] == 0:
                    cursor.execute('''
                        INSERT INTO users (username, password, login_code, created_at, is_active)
                        VALUES (?, ?, ?, ?, 1)
                    ''', ("Admin", "123456", "123456", time.time()))

                cursor.execute("SELECT COUNT(*) FROM users WHERE username = 'testuser'")
                if cursor.fetchone()[0] == 0:
                    cursor.execute('''
                        INSERT INTO users (username, password, login_code, created_at, is_active)
                        VALUES (?, ?, ?, ?, 1)
                    ''', ("testuser", "shadow123", "654321", time.time()))

                u_conn.commit()

            # 2. Initialize Content DB for each source
            for src in sources:
                c_path = self.get_content_db_path(src)
                with sqlite3.connect(c_path) as c_conn:
                    c_conn.execute("PRAGMA journal_mode=WAL;")
                    cursor = c_conn.cursor()
                    cursor.execute('''
                        CREATE TABLE IF NOT EXISTS content_index (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            title TEXT NOT NULL,
                            description TEXT,
                            cover_url TEXT,
                            source_page_url TEXT NOT NULL UNIQUE,
                            genre TEXT
                        )
                    ''')
                    cursor.execute('''
                        CREATE TABLE IF NOT EXISTS episodes (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            content_id INTEGER,
                            season_number INTEGER,
                            episode_number INTEGER,
                            title TEXT,
                            episode_url TEXT,
                            FOREIGN KEY (content_id) REFERENCES content_index (id)
                        )
                    ''')
                    cursor.execute('''
                        CREATE TABLE IF NOT EXISTS video_links (
                            episode_id INTEGER PRIMARY KEY,
                            stream_url TEXT NOT NULL,
                            expires_at REAL NOT NULL,
                            FOREIGN KEY (episode_id) REFERENCES episodes (id)
                        )
                    ''')
                    c_conn.commit()

            # 3. Check for legacy single movies.db migration
            self._migrate_legacy_db()

    def _migrate_legacy_db(self):
        legacy_path = os.path.join(self.db_dir, "movies.db")
        if not os.path.exists(legacy_path):
            return

        try:
            with sqlite3.connect(legacy_path) as leg_conn:
                cursor = leg_conn.cursor()
                cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
                tables = [r[0] for r in cursor.fetchall()]

                # Migrate users if users table exists in legacy
                if "users" in tables:
                    cursor.execute("SELECT * FROM users")
                    users = cursor.fetchall()
                    with self.get_user_connection() as u_conn:
                        u_cursor = u_conn.cursor()
                        for u in users:
                            u_cursor.execute('''
                                INSERT OR IGNORE INTO users (id, username, login_code, password, created_at, is_active)
                                VALUES (?, ?, ?, ?, ?, ?)
                            ''', (u[0], u[1], u[2], u[3] if len(u) > 3 else None, u[4] if len(u) > 4 else time.time(), u[5] if len(u) > 5 else 1))
                        u_conn.commit()

                # Migrate content to default source 'sto'
                if "content_index" in tables:
                    cursor.execute("SELECT * FROM content_index")
                    contents = cursor.fetchall()
                    with self.get_content_connection("sto") as sto_conn:
                        sto_cursor = sto_conn.cursor()
                        for c in contents:
                            sto_cursor.execute('''
                                INSERT OR IGNORE INTO content_index (id, title, description, cover_url, source_page_url, genre)
                                VALUES (?, ?, ?, ?, ?, ?)
                            ''', (c[0], c[1], c[2], c[3], c[4], c[5] if len(c) > 5 else ""))
                        sto_conn.commit()

                if "episodes" in tables:
                    cursor.execute("SELECT * FROM episodes")
                    episodes = cursor.fetchall()
                    with self.get_content_connection("sto") as sto_conn:
                        sto_cursor = sto_conn.cursor()
                        for ep in episodes:
                            sto_cursor.execute('''
                                INSERT OR IGNORE INTO episodes (id, content_id, season_number, episode_number, title, episode_url)
                                VALUES (?, ?, ?, ?, ?, ?)
                            ''', (ep[0], ep[1], ep[2], ep[3], ep[4], ep[5]))
                        sto_conn.commit()

            print("Erfolgreich von alter movies.db in neue modulare DB-Struktur migriert.")
        except Exception as e:
            print(f"Hinweis bei Legacy-Migration: {e}")
