import time
from typing import List, Dict, Optional
from database import DatabaseManager
from models.content import ProgressRequest, WatchlistRequest

KNOWN_GENRES = {
    'abenteuer', 'action', 'actiondrama', 'alltagsleben', 'animation', 'anime', 'comedy',
    'crime', 'doku-soap', 'dokumentarfilm', 'dokumentation', 'documentary', 'drama',
    'dramedy', 'ecchi', 'familie', 'family', 'fantasy', 'harem', 'historie', 'history',
    'horror', 'isekai', 'kinderserie', 'komödie', 'kriegsfilm', 'krimi', 'liebesdrama',
    'liebesfilm', 'mecha', 'music', 'musik', 'mystery', 'psychological', 'romance',
    'romanze', 'romantische komödie', 'romantik', 'sci-fi', 'science fiction', 'scifi',
    'seinen', 'shoujo', 'shounen', 'sitcom', 'slice of life', 'sport', 'supernatural',
    'thriller', 'true crime', 'tv-film', 'war', 'western', 'zeichentrick'
}

class ContentService:
    def __init__(self, db: DatabaseManager):
        self.db = db

    def get_movies(self, source: Optional[str] = "aniworld", page: int = 1, limit: int = 20) -> List[Dict]:
        clean_source = self.db.sanitize_source(source)
        offset = (page - 1) * limit
        with self.db.get_content_connection(clean_source, row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM content_index LIMIT ? OFFSET ?", (limit, offset))
            return [dict(row) for row in cursor.fetchall()]

    def get_genres(self, source: Optional[str] = "aniworld") -> List[str]:
        clean_source = self.db.sanitize_source(source)
        with self.db.get_content_connection(clean_source) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT genre FROM content_index WHERE genre IS NOT NULL AND genre != ''")
            unique_genres = set()
            for row in cursor.fetchall():
                for g in row[0].split(","):
                    cleaned = g.strip()
                    if cleaned.lower() in KNOWN_GENRES:
                        formatted = cleaned.title() if cleaned.islower() else cleaned
                        unique_genres.add(formatted)
            return sorted(list(unique_genres))

    def get_movies_by_genre(self, source: Optional[str] = "aniworld", genre_name: str = "", page: int = 1, limit: int = 20) -> List[Dict]:
        clean_source = self.db.sanitize_source(source)
        offset = (page - 1) * limit
        with self.db.get_content_connection(clean_source, row_factory=True) as conn:
            cursor = conn.cursor()
            if not genre_name or genre_name.lower() in ("alle", "alle serien", "all"):
                cursor.execute("SELECT * FROM content_index LIMIT ? OFFSET ?", (limit, offset))
            else:
                cursor.execute("SELECT * FROM content_index WHERE genre LIKE ? LIMIT ? OFFSET ?", (f"%{genre_name}%", limit, offset))
            return [dict(row) for row in cursor.fetchall()]

    def search_movies(self, source: Optional[str] = "aniworld", query: str = "", genre: str = "", page: int = 1, limit: int = 20) -> List[Dict]:
        clean_source = self.db.sanitize_source(source)
        offset = (page - 1) * limit
        with self.db.get_content_connection(clean_source, row_factory=True) as conn:
            cursor = conn.cursor()
            sql = "SELECT * FROM content_index WHERE 1=1"
            params = []
            if query:
                sql += " AND (title LIKE ? OR description LIKE ?)"
                params.extend([f"%{query}%", f"%{query}%"])
            if genre:
                for g in genre.split(','):
                    if g.strip():
                        sql += " AND genre LIKE ?"
                        params.append(f"%{g.strip()}%")
            sql += " LIMIT ? OFFSET ?"
            params.extend([limit, offset])
            cursor.execute(sql, tuple(params))
            return [dict(row) for row in cursor.fetchall()]

    def log_search(self, user_id: int, source: Optional[str] = "aniworld", query: str = "", genre: str = "", results_count: int = 0):
        if not query and not genre:
            return
        clean_source = self.db.sanitize_source(source)
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                INSERT INTO search_logs (user_id, source, query, genre, results_count, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
            ''', (user_id, clean_source, query.strip(), genre.strip(), results_count, time.time()))
            conn.commit()

    def get_source_page_url(self, source: Optional[str] = "aniworld", content_id: int = 0) -> Optional[str]:
        clean_source = self.db.sanitize_source(source)
        with self.db.get_content_connection(clean_source, row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT source_page_url FROM content_index WHERE id = ?", (content_id,))
            row = cursor.fetchone()
            return row["source_page_url"] if row else None

    def get_episodes(self, source: Optional[str] = "aniworld", content_id: int = 0, user_id: int = 1) -> Dict[int, List[Dict]]:
        clean_source = self.db.sanitize_source(source)
        user_progress = {}
        with self.db.get_user_connection(row_factory=True) as u_conn:
            u_cursor = u_conn.cursor()
            u_cursor.execute('''
                SELECT episode_id, is_completed, progress_seconds, duration_seconds, last_watched_at
                FROM watch_progress
                WHERE user_id = ? AND source = ? AND content_id = ?
            ''', (user_id, clean_source, content_id))
            for row in u_cursor.fetchall():
                user_progress[row["episode_id"]] = dict(row)

        with self.db.get_content_connection(clean_source, row_factory=True) as c_conn:
            c_cursor = c_conn.cursor()
            c_cursor.execute('''
                SELECT * FROM episodes WHERE content_id = ? ORDER BY season_number, episode_number
            ''', (content_id,))
            seasons = {}
            for row in c_cursor.fetchall():
                ep_dict = dict(row)
                ep_id = ep_dict["id"]
                prog = user_progress.get(ep_id, {})
                ep_dict["is_completed"] = bool(prog.get("is_completed", False))
                ep_dict["progress_seconds"] = float(prog.get("progress_seconds", 0.0))
                ep_dict["duration_seconds"] = float(prog.get("duration_seconds") or 0.0)
                ep_dict["last_watched_at"] = float(prog.get("last_watched_at", 0.0))
                
                s_num = ep_dict["season_number"]
                seasons.setdefault(s_num, []).append(ep_dict)
            return seasons

    def update_progress(self, user_id: int, source: Optional[str] = "aniworld", req: ProgressRequest = None) -> dict:
        clean_source = self.db.sanitize_source(source or (req.source if hasattr(req, 'source') else "aniworld"))
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            current_time = time.time()
            cursor.execute('''
                INSERT INTO watch_progress (user_id, source, episode_id, content_id, progress_seconds, duration_seconds, is_completed, last_watched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, source, episode_id) DO UPDATE SET
                    progress_seconds = excluded.progress_seconds,
                    duration_seconds = excluded.duration_seconds,
                    is_completed = excluded.is_completed,
                    last_watched_at = excluded.last_watched_at
            ''', (user_id, clean_source, req.episode_id, req.content_id, req.progress_seconds, req.duration_seconds, req.is_completed, current_time))

            cursor.execute('''
                SELECT id, timestamp FROM watch_logs
                WHERE user_id = ? AND source = ? AND episode_id = ?
                ORDER BY timestamp DESC LIMIT 1
            ''', (user_id, clean_source, req.episode_id))
            last_log = cursor.fetchone()

            if last_log and (current_time - last_log[1]) < 30:
                cursor.execute('''
                    UPDATE watch_logs
                    SET progress_seconds = ?, duration_seconds = ?, is_completed = ?, timestamp = ?
                    WHERE id = ?
                ''', (req.progress_seconds, req.duration_seconds, req.is_completed, current_time, last_log[0]))
            else:
                cursor.execute('''
                    INSERT INTO watch_logs (user_id, source, content_id, episode_id, progress_seconds, duration_seconds, is_completed, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ''', (user_id, clean_source, req.content_id, req.episode_id, req.progress_seconds, req.duration_seconds, req.is_completed, current_time))

            conn.commit()
        return {"status": "success"}

    def get_continue_watching(self, user_id: int, source: Optional[str] = None) -> List[Dict]:
        results = []
        sources_to_query = [self.db.sanitize_source(source)] if source else ["aniworld", "sto", "filmo"]

        with self.db.get_user_connection(row_factory=True) as u_conn:
            u_cursor = u_conn.cursor()

            for src in sources_to_query:
                u_cursor.execute('''
                    SELECT content_id, MAX(last_watched_at) as last_watched
                    FROM watch_progress
                    WHERE user_id = ? AND source = ?
                    GROUP BY content_id
                    ORDER BY last_watched DESC
                    LIMIT 20
                ''', (user_id, src))
                series_rows = u_cursor.fetchall()

                for s_row in series_rows:
                    c_id = s_row["content_id"]

                    with self.db.get_content_connection(src, row_factory=True) as c_conn:
                        c_cursor = c_conn.cursor()
                        c_cursor.execute("SELECT * FROM content_index WHERE id = ?", (c_id,))
                        content_info = c_cursor.fetchone()
                        if not content_info:
                            continue

                        c_cursor.execute("SELECT * FROM episodes WHERE content_id = ? ORDER BY season_number, episode_number", (c_id,))
                        episodes = [dict(r) for r in c_cursor.fetchall()]

                    if not episodes:
                        continue

                    u_cursor.execute('''
                        SELECT episode_id, is_completed, progress_seconds, duration_seconds, last_watched_at
                        FROM watch_progress
                        WHERE user_id = ? AND source = ? AND content_id = ?
                    ''', (user_id, src, c_id))
                    progress_map = {r["episode_id"]: dict(r) for r in u_cursor.fetchall()}

                    for ep in episodes:
                        prog = progress_map.get(ep["id"], {})
                        ep["is_completed"] = bool(prog.get("is_completed", False))
                        ep["progress_seconds"] = float(prog.get("progress_seconds", 0.0))
                        ep["duration_seconds"] = float(prog.get("duration_seconds", 0.0))
                        ep["last_watched_at"] = float(prog.get("last_watched_at", 0.0))

                    watched_eps = [ep for ep in episodes if ep["last_watched_at"] > 0]
                    if not watched_eps:
                        continue

                    most_recent = max(watched_eps, key=lambda x: x["last_watched_at"])

                    continue_ep = None
                    if most_recent["is_completed"]:
                        idx = episodes.index(most_recent)
                        if idx + 1 < len(episodes):
                            continue_ep = episodes[idx + 1]
                        else:
                            continue
                    else:
                        continue_ep = most_recent

                    if continue_ep:
                        results.append({
                            "id": content_info["id"],
                            "title": content_info["title"],
                            "description": content_info["description"],
                            "cover_url": content_info["cover_url"],
                            "genre": content_info["genre"],
                            "source": src,
                            "episode_id": continue_ep["id"],
                            "season_number": continue_ep["season_number"],
                            "episode_number": continue_ep["episode_number"],
                            "episode_title": continue_ep["title"],
                            "progress_seconds": continue_ep["progress_seconds"],
                            "duration_seconds": continue_ep["duration_seconds"],
                            "last_watched_at": continue_ep["last_watched_at"]
                        })

        results.sort(key=lambda x: x.get("last_watched_at", 0), reverse=True)
        return results[:20]

    def update_watchlist(self, user_id: int, source: Optional[str] = "aniworld", req: WatchlistRequest = None) -> dict:
        clean_source = self.db.sanitize_source(source or (req.source if hasattr(req, 'source') else "aniworld"))
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            if req.is_watchlisted:
                cursor.execute('''
                    INSERT OR IGNORE INTO watchlist (user_id, source, content_id, added_at)
                    VALUES (?, ?, ?, ?)
                ''', (user_id, clean_source, req.content_id, time.time()))
            else:
                cursor.execute('''
                    DELETE FROM watchlist WHERE user_id = ? AND source = ? AND content_id = ?
                ''', (user_id, clean_source, req.content_id))
            conn.commit()
        return {"status": "success"}

    def get_watchlist_status(self, user_id: int, source: Optional[str] = "aniworld", content_id: int = 0) -> dict:
        clean_source = self.db.sanitize_source(source)
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT 1 FROM watchlist WHERE user_id = ? AND source = ? AND content_id = ?
            ''', (user_id, clean_source, content_id))
            return {"is_watchlisted": bool(cursor.fetchone())}

    def get_watchlist(self, user_id: int, source: Optional[str] = None, page: int = 1, limit: int = 20) -> List[Dict]:
        offset = (page - 1) * limit
        results = []
        sources_to_query = [self.db.sanitize_source(source)] if source else ["aniworld", "sto", "filmo"]

        with self.db.get_user_connection(row_factory=True) as u_conn:
            u_cursor = u_conn.cursor()

            for src in sources_to_query:
                u_cursor.execute('''
                    SELECT content_id, added_at
                    FROM watchlist
                    WHERE user_id = ? AND source = ?
                    ORDER BY added_at DESC
                ''', (user_id, src))
                w_items = u_cursor.fetchall()

                with self.db.get_content_connection(src, row_factory=True) as c_conn:
                    c_cursor = c_conn.cursor()
                    for item in w_items:
                        c_id = item["content_id"]
                        c_cursor.execute("SELECT * FROM content_index WHERE id = ?", (c_id,))
                        c_row = c_cursor.fetchone()
                        if c_row:
                            c_dict = dict(c_row)
                            c_dict["source"] = src
                            c_dict["added_at"] = item["added_at"]
                            results.append(c_dict)

        results.sort(key=lambda x: x.get("added_at", 0), reverse=True)
        return results[offset:offset + limit]
