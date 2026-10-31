import os
import re
import sqlite3
import threading
from typing import List, Optional

from passwords import hash_password, is_hashed

SUPPORTED_SOURCES = ["aniworld", "sto", "filmo"]

class DatabaseManager:
    def __init__(self, db_dir: Optional[str] = None):
        if db_dir is None:
            db_dir = os.environ.get("BLJADFLIX_DB_DIR") or os.path.dirname(os.path.abspath(__file__))
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

    def init_db(self, sources: Optional[List[str]] = None):
        if not sources:
            sources = SUPPORTED_SOURCES

        with self.lock:
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

                cursor.execute("SELECT id, password FROM users WHERE password IS NOT NULL AND password != ''")
                for user_id, stored in cursor.fetchall():
                    if not is_hashed(stored):
                        cursor.execute("UPDATE users SET password = ? WHERE id = ?", (hash_password(stored), user_id))

                u_conn.commit()

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
