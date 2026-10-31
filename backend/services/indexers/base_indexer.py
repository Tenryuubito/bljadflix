import random
import requests
import threading
import time
from typing import Tuple, List, Optional
from database import DatabaseManager
from config import Config
from services.indexer_logger import indexer_logger

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
]

class BaseIndexer:
    def __init__(self, source_id: str, base_url: str, db: DatabaseManager, config: Config):
        self.source_id = source_id
        self.base_url = base_url.rstrip("/")
        self.db = db
        self.config = config
        self.write_lock = threading.Lock()

    def log(self, message: str, level: str = "INFO"):
        indexer_logger.log(message, level=level, source=self.source_id)

    def get_random_headers(self) -> dict:
        ua = random.choice(USER_AGENTS)
        sec_ch_ua = '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"'
        if "Firefox" in ua:
            sec_ch_ua = ""
        elif "Edg" in ua:
            sec_ch_ua = '"Not_A Brand";v="8", "Chromium";v="120", "Microsoft Edge";v="120"'
            
        headers = {
            "User-Agent": ua,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": random.choice(["de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7", "en-US,en;q=0.5", "de,en;q=0.7"]),
            "DNT": "1",
            "Upgrade-Insecure-Requests": "1"
        }
        if sec_ch_ua:
            headers["Sec-Ch-Ua"] = sec_ch_ua
            headers["Sec-Ch-Ua-Mobile"] = "?0"
            headers["Sec-Ch-Ua-Platform"] = '"Windows"' if "Windows" in ua else '"macOS"' if "Mac" in ua else '"Linux"'
        return headers

    def fetch_with_retry(self, url: str, max_retries: int = 3) -> requests.Response:
        for attempt in range(max_retries):
            time.sleep(random.uniform(0.3, 1.5))
            headers = self.get_random_headers()
            session = requests.Session()
            try:
                res = session.get(url, headers=headers, timeout=12)
                if res.status_code == 200:
                    return res
                elif res.status_code in (429, 403):
                    time.sleep(random.uniform(1.5, 3.5))
                    continue
                else:
                    continue
            except (requests.exceptions.Timeout, requests.exceptions.ConnectionError):
                continue
        raise Exception(f"Fehlgeschlagen nach {max_retries} Versuchen für URL: {url}")

    def get_indexed_urls(self) -> set:
        try:
            with self.db.get_content_connection(self.source_id) as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT source_page_url FROM content_index")
                return {row[0].rstrip('/') for row in cursor.fetchall() if row[0]}
        except Exception as e:
            self.log(f"Fehler beim Abrufen bereits indexierter URLs: {e}", "WARN")
            return set()

    def process_single_series(self, s_url: str) -> Tuple[str, bool, str]:
        raise NotImplementedError("process_single_series muss in abgeleiteter Klasse implementiert werden.")

    def scrape_all_series(self, limit: Optional[int] = None, incremental: bool = True):
        raise NotImplementedError("scrape_all_series muss in abgeleiteter Klasse implementiert werden.")

    def save_series_to_db(self, title: str, description: str, cover_url: str, source_page_url: str, genre_str: str, episodes_data: List[Tuple[int, int, str, str]]):
        with self.write_lock:
            with self.db.get_content_connection(self.source_id, timeout=20.0) as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    INSERT INTO content_index (title, description, cover_url, source_page_url, genre)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(source_page_url) DO UPDATE SET
                        title=excluded.title, description=excluded.description,
                        cover_url=excluded.cover_url, genre=excluded.genre
                ''', (title, description, cover_url, source_page_url, genre_str))

                cursor.execute("SELECT id FROM content_index WHERE source_page_url=?", (source_page_url,))
                row = cursor.fetchone()
                content_id = row[0] if row else cursor.lastrowid

                cursor.execute("SELECT id, season_number, episode_number FROM episodes WHERE content_id = ?", (content_id,))
                existing_eps = {(r[1], r[2]): r[0] for r in cursor.fetchall()}

                for s_num, e_num, ep_title, full_ep_url in episodes_data:
                    if (s_num, e_num) in existing_eps:
                        cursor.execute('''
                            UPDATE episodes SET title = ?, episode_url = ? WHERE id = ?
                        ''', (ep_title, full_ep_url, existing_eps[(s_num, e_num)]))
                    else:
                        cursor.execute('''
                            INSERT INTO episodes (content_id, season_number, episode_number, title, episode_url)
                            VALUES (?, ?, ?, ?, ?)
                        ''', (content_id, s_num, e_num, ep_title, full_ep_url))
                conn.commit()
