import hashlib
import io
import os
import threading
import time
from typing import Optional
from urllib.parse import urlparse

import requests

from config import Config
from database import DatabaseManager

COVER_MAX_WIDTH = 300
COVER_JPEG_QUALITY = 80
FETCH_TIMEOUT_SECONDS = 10
MAX_SOURCE_BYTES = 8 * 1024 * 1024
FAILURE_RETRY_SECONDS = 3600
LOCK_STRIPES = 64

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


class ImageService:
    def __init__(self, db: DatabaseManager, config: Config, cache_dir: Optional[str] = None):
        self.db = db
        self.config = config
        self.cache_dir = cache_dir or os.path.join(db.db_dir, "image_cache")
        self._locks = [threading.Lock() for _ in range(LOCK_STRIPES)]
        self._failures: dict = {}
        self._failures_guard = threading.Lock()

    def get_cover_path(self, source: Optional[str], content_id: int) -> Optional[str]:
        clean_source = self.db.sanitize_source(source)
        if clean_source not in (self.config.sources or {}):
            return None

        cover_url = self._lookup_cover_url(clean_source, content_id)
        if not cover_url:
            return None

        cache_path = os.path.join(self.cache_dir, clean_source, hashlib.sha1(cover_url.encode("utf-8")).hexdigest() + ".jpg")
        if os.path.exists(cache_path):
            return cache_path

        if self._failed_recently(cache_path):
            return None

        with self._lock_for(cache_path):
            if os.path.exists(cache_path):
                return cache_path
            if self._failed_recently(cache_path):
                return None
            data = self._fetch(cover_url)
            if data is None:
                with self._failures_guard:
                    self._failures[cache_path] = time.time()
                return None
            os.makedirs(os.path.dirname(cache_path), exist_ok=True)
            tmp_path = cache_path + ".tmp"
            with open(tmp_path, "wb") as f:
                f.write(self._shrink(data))
            os.replace(tmp_path, cache_path)
        return cache_path

    def _lookup_cover_url(self, source: str, content_id: int) -> Optional[str]:
        if not os.path.exists(self.db.get_content_db_path(source)):
            return None
        with self.db.get_content_connection(source, row_factory=True) as conn:
            row = conn.execute("SELECT cover_url FROM content_index WHERE id = ?", (content_id,)).fetchone()
        if not row or not row["cover_url"]:
            return None
        url = row["cover_url"].strip()
        if urlparse(url).scheme not in ("http", "https"):
            return None
        return url

    def _fetch(self, url: str) -> Optional[bytes]:
        parsed = urlparse(url)
        headers = {"User-Agent": USER_AGENT, "Referer": f"{parsed.scheme}://{parsed.netloc}/"}
        try:
            with requests.get(url, headers=headers, timeout=FETCH_TIMEOUT_SECONDS, stream=True) as res:
                if res.status_code != 200 or not res.headers.get("Content-Type", "image/").startswith("image/"):
                    return None
                buffer = io.BytesIO()
                for chunk in res.iter_content(64 * 1024):
                    buffer.write(chunk)
                    if buffer.tell() > MAX_SOURCE_BYTES:
                        return None
                return buffer.getvalue() or None
        except requests.RequestException:
            return None

    def _shrink(self, data: bytes) -> bytes:
        try:
            from PIL import Image
        except ImportError:
            return data
        try:
            with Image.open(io.BytesIO(data)) as img:
                img = img.convert("RGB")
                if img.width > COVER_MAX_WIDTH:
                    height = round(img.height * COVER_MAX_WIDTH / img.width)
                    img = img.resize((COVER_MAX_WIDTH, height), Image.LANCZOS)
                out = io.BytesIO()
                img.save(out, format="JPEG", quality=COVER_JPEG_QUALITY, optimize=True, progressive=True)
                return out.getvalue()
        except Exception:
            return data

    def _failed_recently(self, key: str) -> bool:
        now = time.time()
        with self._failures_guard:
            failed_at = self._failures.get(key)
            if failed_at is None:
                return False
            if now - failed_at < FAILURE_RETRY_SECONDS:
                return True
            del self._failures[key]
            return False

    def _lock_for(self, key: str) -> threading.Lock:
        return self._locks[int(hashlib.sha1(key.encode("utf-8")).hexdigest(), 16) % LOCK_STRIPES]
