import threading
from typing import Optional, Dict, Tuple
from fastapi import BackgroundTasks

from database import DatabaseManager
from config import Config
from services.indexer_logger import indexer_logger
from .indexers.base_indexer import BaseIndexer
from .indexers.aniworld_indexer import AniWorldIndexer
from .indexers.sto_indexer import StoIndexer
from .indexers.filmo_indexer import FilmoIndexer

class IndexerService:
    def __init__(self, db: DatabaseManager, config: Config):
        self.db = db
        self.config = config
        self.sync_lock = threading.Lock()
        self.is_syncing = False
        self.is_indexing = False
        self.active_reindexes = set()
        self.indexers: Dict[str, BaseIndexer] = {
            "aniworld": AniWorldIndexer(db, config),
            "sto": StoIndexer(db, config),
            "filmo": FilmoIndexer(db, config)
        }

    def get_indexer(self, source: Optional[str] = None) -> BaseIndexer:
        clean = self.db.sanitize_source(source)
        if clean in self.indexers:
            return self.indexers[clean]
        # Fallback to aniworld or sto
        return self.indexers.get("aniworld") or self.indexers.get("sto")

    def process_single_series(self, s_url: str, source: Optional[str] = "aniworld") -> Tuple[str, bool, str]:
        indexer = self.get_indexer(source)
        return indexer.process_single_series(s_url)

    def process_single_series_background(self, s_url: str, source: Optional[str] = "aniworld") -> Tuple[str, bool, str]:
        with self.sync_lock:
            if s_url in self.active_reindexes:
                print(f"Reindex für {s_url} ({source}) läuft bereits im Hintergrund.")
                return s_url, True, "Bereits in Bearbeitung"
            self.active_reindexes.add(s_url)

        try:
            return self.process_single_series(s_url, source)
        finally:
            with self.sync_lock:
                self.active_reindexes.discard(s_url)

    def scrape_all_series(self, source: Optional[str] = None, limit: Optional[int] = None, incremental: bool = True):
        with self.sync_lock:
            if self.is_indexing:
                indexer_logger.log("Indexierung läuft bereits im Hintergrund.", "WARN", "SYSTEM")
                return
            self.is_indexing = True

        mode_str = "inkrementelle " if incremental else "vollständige "
        indexer_logger.log(f"Starte {mode_str}globale Indexierung (Quelle: {source or 'all'})...", "INFO", "SYSTEM")

        try:
            self.db.init_db()
            if source and source != "all":
                indexer = self.get_indexer(source)
                indexer.scrape_all_series(limit=limit, incremental=incremental)
            else:
                for src_id, indexer in self.indexers.items():
                    indexer_logger.log(f"--- Starte automatische Indexierung für Quelle: {src_id.upper()} ---", "INFO", "SYSTEM")
                    try:
                        indexer.scrape_all_series(limit=limit, incremental=incremental)
                    except Exception as e:
                        indexer_logger.log(f"Fehler bei Indexierung von Quelle '{src_id}': {e}", "ERROR", "SYSTEM")
        finally:
            with self.sync_lock:
                self.is_indexing = False
            indexer_logger.log("Globale Indexierung abgeschlossen.", "SUCCESS", "SYSTEM")

    def get_sync_status(self) -> dict:
        return {"is_syncing": self.is_syncing, "is_indexing": self.is_indexing}

    def sync_user_content(self, user_id: int, background_tasks: BackgroundTasks, source: Optional[str] = "aniworld") -> dict:
        clean_source = self.db.sanitize_source(source)
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT content_id FROM watchlist WHERE user_id = ? AND source = ?
                UNION
                SELECT content_id FROM watch_progress WHERE user_id = ? AND source = ?
            ''', (user_id, clean_source, user_id, clean_source))
            content_ids = [row["content_id"] for row in cursor.fetchall()]

        if not content_ids:
            return {"status": "success", "synced_count": 0}

        # Fetch source_page_urls from content db
        urls = []
        with self.db.get_content_connection(clean_source, row_factory=True) as c_conn:
            c_cursor = c_conn.cursor()
            for c_id in content_ids:
                c_cursor.execute("SELECT source_page_url FROM content_index WHERE id = ?", (c_id,))
                r = c_cursor.fetchone()
                if r and r["source_page_url"]:
                    urls.append(r["source_page_url"])

        if not urls:
            return {"status": "success", "synced_count": 0}

        with self.sync_lock:
            if self.is_syncing:
                return {"status": "already_syncing", "synced_count": 0}
            self.is_syncing = True

        def sync_task(urls_to_sync, src):
            indexer = self.get_indexer(src)
            for url in urls_to_sync:
                try:
                    indexer.process_single_series(url)
                except Exception as e:
                    print(f"Fehler bei Sync von {url} ({src}): {e}")
            with self.sync_lock:
                self.is_syncing = False

        background_tasks.add_task(sync_task, urls, clean_source)
        return {"status": "success", "synced_count": len(urls)}
