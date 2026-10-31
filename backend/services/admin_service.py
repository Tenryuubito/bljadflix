import time
from typing import List, Dict, Optional, Any
from fastapi import HTTPException
from database import DatabaseManager

class AdminService:
    def __init__(self, db: DatabaseManager):
        self.db = db

    def _get_db_conn(self, db_name: str, row_factory: bool = True):
        clean_name = self.db.sanitize_source(db_name) if db_name not in ["users", "user"] else "users"
        if clean_name == "users":
            return self.db.get_user_connection(row_factory=row_factory)
        else:
            return self.db.get_content_connection(clean_name, row_factory=row_factory)

    def get_dashboard_stats(self) -> Dict[str, Any]:
        stats = {
            "total_users": 0,
            "active_users": 0,
            "active_sessions": 0,
            "total_watch_hours": 0.0,
            "total_watch_events": 0,
            "completed_episodes": 0,
            "in_progress_episodes": 0,
            "content_counts": {}
        }

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*), SUM(CASE WHEN is_active = 1 THEN 1 ELSE 0 END) FROM users")
            row = cursor.fetchone()
            if row:
                stats["total_users"] = row[0] or 0
                stats["active_users"] = row[1] or 0

            cursor.execute("SELECT COUNT(*) FROM user_sessions")
            s_row = cursor.fetchone()
            if s_row:
                stats["active_sessions"] = s_row[0] or 0

            cursor.execute('''
                SELECT
                    SUM(progress_seconds) as total_seconds,
                    COUNT(*) as total_records,
                    SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) as completed,
                    SUM(CASE WHEN is_completed = 0 AND progress_seconds > 0 THEN 1 ELSE 0 END) as in_progress
                FROM watch_progress
            ''')
            w_row = cursor.fetchone()
            if w_row:
                total_sec = w_row["total_seconds"] or 0.0
                stats["total_watch_hours"] = round(total_sec / 3600.0, 1)
                stats["total_watch_events"] = w_row["total_records"] or 0
                stats["completed_episodes"] = w_row["completed"] or 0
                stats["in_progress_episodes"] = w_row["in_progress"] or 0

        for src in ["aniworld", "sto", "filmo"]:
            try:
                with self.db.get_content_connection(src) as c_conn:
                    c_cursor = c_conn.cursor()
                    c_cursor.execute("SELECT COUNT(*) FROM content_index")
                    stats["content_counts"][src] = c_cursor.fetchone()[0]
            except Exception:
                stats["content_counts"][src] = 0

        return stats

    def get_top_watched_content(self, limit: int = 10) -> List[Dict[str, Any]]:
        top_items = []
        with self.db.get_user_connection(row_factory=True) as u_conn:
            u_cursor = u_conn.cursor()
            u_cursor.execute('''
                SELECT source, content_id,
                       COUNT(DISTINCT user_id) as unique_viewers,
                       COUNT(*) as total_views,
                       SUM(progress_seconds) as total_watch_time,
                       MAX(last_watched_at) as last_activity
                FROM watch_progress
                GROUP BY source, content_id
                ORDER BY total_watch_time DESC
                LIMIT ?
            ''', (limit,))
            rows = u_cursor.fetchall()

            for r in rows:
                src = r["source"]
                c_id = r["content_id"]
                title = f"Content ID {c_id}"
                cover_url = ""
                genre = ""

                try:
                    with self.db.get_content_connection(src, row_factory=True) as c_conn:
                        c_cursor = c_conn.cursor()
                        c_cursor.execute("SELECT title, cover_url, genre FROM content_index WHERE id = ?", (c_id,))
                        c_row = c_cursor.fetchone()
                        if c_row:
                            title = c_row["title"]
                            cover_url = c_row["cover_url"]
                            genre = c_row["genre"]
                except Exception:
                    pass

                top_items.append({
                    "source": src,
                    "content_id": c_id,
                    "title": title,
                    "cover_url": cover_url,
                    "genre": genre,
                    "unique_viewers": r["unique_viewers"],
                    "total_views": r["total_views"],
                    "total_watch_hours": round((r["total_watch_time"] or 0) / 3600.0, 1),
                    "last_activity": r["last_activity"]
                })

        return top_items

    def get_activity_charts_data(self) -> Dict[str, Any]:
        source_breakdown = {}
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT source, COUNT(*) as cnt, SUM(progress_seconds) as total_sec
                FROM watch_progress
                GROUP BY source
            ''')
            for row in cursor.fetchall():
                source_breakdown[row["source"]] = {
                    "count": row["cnt"],
                    "hours": round((row["total_sec"] or 0) / 3600.0, 1)
                }

            current_time = time.time()
            seven_days_ago = current_time - (7 * 86400)
            
            cursor.execute('''
                SELECT strftime('%Y-%m-%d', datetime(timestamp, 'unixepoch', 'localtime')) as watch_date,
                       COUNT(*) as event_count,
                       SUM(progress_seconds) as total_sec
                FROM watch_logs
                WHERE timestamp >= ?
                GROUP BY watch_date
                ORDER BY watch_date ASC
            ''', (seven_days_ago,))
            daily_rows = cursor.fetchall()

            if not daily_rows:
                cursor.execute('''
                    SELECT strftime('%Y-%m-%d', datetime(last_watched_at, 'unixepoch', 'localtime')) as watch_date,
                           COUNT(*) as event_count,
                           SUM(progress_seconds) as total_sec
                    FROM watch_progress
                    WHERE last_watched_at >= ?
                    GROUP BY watch_date
                    ORDER BY watch_date ASC
                ''', (seven_days_ago,))
                daily_rows = cursor.fetchall()

            daily_labels = [r["watch_date"] for r in daily_rows if r["watch_date"]]
            daily_events = [r["event_count"] for r in daily_rows if r["watch_date"]]
            daily_hours = [round((r["total_sec"] or 0) / 3600.0, 2) for r in daily_rows if r["watch_date"]]

        return {
            "source_breakdown": source_breakdown,
            "daily_activity": {
                "labels": daily_labels,
                "events": daily_events,
                "hours": daily_hours
            }
        }

    def get_watch_logs(
        self,
        user_id: Optional[int] = None,
        source: Optional[str] = None,
        search: Optional[str] = None,
        page: int = 1,
        limit: int = 50
    ) -> Dict[str, Any]:
        offset = (page - 1) * limit
        logs = []
        total_count = 0

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()

            cursor.execute("SELECT COUNT(*) FROM watch_logs")
            logs_cnt = cursor.fetchone()[0]

            table_to_use = "watch_logs" if logs_cnt > 0 else "watch_progress"
            ts_col = "timestamp" if logs_cnt > 0 else "last_watched_at"

            query_sql = f'''
                SELECT l.*, u.username
                FROM {table_to_use} l
                LEFT JOIN users u ON l.user_id = u.id
                WHERE 1=1
            '''
            params = []

            if user_id:
                query_sql += " AND l.user_id = ?"
                params.append(user_id)

            if source and source.lower() != "all":
                query_sql += " AND l.source = ?"
                params.append(self.db.sanitize_source(source))

            count_sql = f"SELECT COUNT(*) FROM ({query_sql})"
            cursor.execute(count_sql, tuple(params))
            total_count = cursor.fetchone()[0]

            query_sql += f" ORDER BY l.{ts_col} DESC LIMIT ? OFFSET ?"
            params.extend([limit, offset])

            cursor.execute(query_sql, tuple(params))
            raw_rows = cursor.fetchall()

            content_cache = {}

            for r in raw_rows:
                r_dict = dict(r)
                src = r_dict["source"]
                c_id = r_dict["content_id"]
                ep_id = r_dict["episode_id"]

                cache_key = (src, c_id, ep_id)
                if cache_key not in content_cache:
                    series_title = f"Serie #{c_id}"
                    cover_url = ""
                    ep_title = f"Episode #{ep_id}"
                    s_num, ep_num = 1, 1

                    try:
                        with self.db.get_content_connection(src, row_factory=True) as c_conn:
                            c_cursor = c_conn.cursor()
                            c_cursor.execute("SELECT title, cover_url FROM content_index WHERE id = ?", (c_id,))
                            c_info = c_cursor.fetchone()
                            if c_info:
                                series_title = c_info["title"]
                                cover_url = c_info["cover_url"]

                            c_cursor.execute("SELECT season_number, episode_number, title FROM episodes WHERE id = ?", (ep_id,))
                            ep_info = c_cursor.fetchone()
                            if ep_info:
                                s_num = ep_info["season_number"]
                                ep_num = ep_info["episode_number"]
                                ep_title = ep_info["title"] or f"Staffel {s_num} Folge {ep_num}"
                    except Exception:
                        pass

                    content_cache[cache_key] = {
                        "series_title": series_title,
                        "cover_url": cover_url,
                        "season_number": s_num,
                        "episode_number": ep_num,
                        "episode_title": ep_title
                    }

                meta = content_cache[cache_key]
                r_dict.update(meta)
                r_dict["timestamp"] = r_dict.get("timestamp") or r_dict.get("last_watched_at") or 0.0

                if search and search.strip():
                    term = search.lower().strip()
                    m_user = (r_dict.get("username") or "").lower()
                    m_title = (r_dict.get("series_title") or "").lower()
                    m_ep = (r_dict.get("episode_title") or "").lower()
                    if term not in m_user and term not in m_title and term not in m_ep:
                        continue

                logs.append(r_dict)

        return {
            "total": total_count,
            "page": page,
            "limit": limit,
            "logs": logs
        }

    def get_users_list(self) -> List[Dict[str, Any]]:
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT u.id, u.username, u.login_code, u.password, u.created_at, u.is_active,
                       s.device_id, s.device_name, s.logged_in_at, s.last_active_at,
                       (SELECT COUNT(*) FROM watch_progress WHERE user_id = u.id) as watch_count
                FROM users u
                LEFT JOIN user_sessions s ON u.id = s.user_id
                ORDER BY u.id ASC
            ''')
            return [dict(row) for row in cursor.fetchall()]

    def toggle_user_status(self, user_id: int) -> Dict[str, Any]:
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT is_active FROM users WHERE id = ?", (user_id,))
            row = cursor.fetchone()
            if not row:
                return {"status": "error", "message": "Benutzer nicht gefunden"}
            new_status = not bool(row[0])
            cursor.execute("UPDATE users SET is_active = ? WHERE id = ?", (int(new_status), user_id))
            conn.commit()
            return {"status": "success", "is_active": new_status}

    def reset_user_session(self, user_id: int) -> Dict[str, Any]:
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM user_sessions WHERE user_id = ?", (user_id,))
            conn.commit()
            return {"status": "success", "message": "Sitzung erfolgreich zurückgesetzt"}

    def get_user_details(self, user_id: int) -> Dict[str, Any]:
        user_info = None
        session_info = None

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
            u_row = cursor.fetchone()
            if not u_row:
                return {"status": "error", "message": "Benutzer nicht gefunden"}
            user_info = dict(u_row)

            cursor.execute("SELECT * FROM user_sessions WHERE user_id = ?", (user_id,))
            s_row = cursor.fetchone()
            if s_row:
                session_info = dict(s_row)

            cursor.execute('''
                SELECT
                    COUNT(*) as total_records,
                    SUM(progress_seconds) as total_seconds,
                    SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) as completed,
                    SUM(CASE WHEN is_completed = 0 AND progress_seconds > 0 THEN 1 ELSE 0 END) as in_progress
                FROM watch_progress
                WHERE user_id = ?
            ''', (user_id,))
            wp_row = cursor.fetchone()
            total_records = wp_row["total_records"] or 0
            total_sec = wp_row["total_seconds"] or 0.0
            completed_cnt = wp_row["completed"] or 0
            in_progress_cnt = wp_row["in_progress"] or 0

            cursor.execute("SELECT COUNT(*) FROM watchlist WHERE user_id = ?", (user_id,))
            watchlist_cnt = cursor.fetchone()[0]

            cursor.execute("SELECT COUNT(*) FROM search_logs WHERE user_id = ?", (user_id,))
            search_cnt = cursor.fetchone()[0]

            cursor.execute('''
                SELECT source, COUNT(*) as cnt, SUM(progress_seconds) as total_sec
                FROM watch_progress
                WHERE user_id = ?
                GROUP BY source
            ''', (user_id,))
            source_breakdown = {}
            favorite_source = "Keine"
            max_src_time = -1
            for r in cursor.fetchall():
                src = r["source"]
                sec = r["total_sec"] or 0.0
                hrs = round(sec / 3600.0, 1)
                source_breakdown[src] = {"count": r["cnt"], "hours": hrs}
                if sec > max_src_time:
                    max_src_time = sec
                    favorite_source = src.upper()

            fourteen_days_ago = time.time() - (14 * 86400)
            cursor.execute('''
                SELECT strftime('%Y-%m-%d', datetime(timestamp, 'unixepoch', 'localtime')) as watch_date,
                       COUNT(*) as event_count,
                       SUM(progress_seconds) as total_sec
                FROM watch_logs
                WHERE user_id = ? AND timestamp >= ?
                GROUP BY watch_date
                ORDER BY watch_date ASC
            ''', (user_id, fourteen_days_ago))
            daily_rows = cursor.fetchall()

            if not daily_rows:
                cursor.execute('''
                    SELECT strftime('%Y-%m-%d', datetime(last_watched_at, 'unixepoch', 'localtime')) as watch_date,
                           COUNT(*) as event_count,
                           SUM(progress_seconds) as total_sec
                    FROM watch_progress
                    WHERE user_id = ? AND last_watched_at >= ?
                    GROUP BY watch_date
                    ORDER BY watch_date ASC
                ''', (user_id, fourteen_days_ago))
                daily_rows = cursor.fetchall()

            daily_labels = [r["watch_date"] for r in daily_rows if r["watch_date"]]
            daily_hours = [round((r["total_sec"] or 0) / 3600.0, 2) for r in daily_rows if r["watch_date"]]

            cursor.execute('''
                SELECT CAST(strftime('%H', datetime(timestamp, 'unixepoch', 'localtime')) AS INTEGER) as hr,
                       COUNT(*) as cnt
                FROM watch_logs
                WHERE user_id = ?
                GROUP BY hr
            ''', (user_id,))
            hourly_map = {r["hr"]: r["cnt"] for r in cursor.fetchall()}
            hourly_activity = [hourly_map.get(h, 0) for h in range(24)]

            genre_counts = {}
            cursor.execute("SELECT DISTINCT source, content_id FROM watch_progress WHERE user_id = ?", (user_id,))
            watched_contents = cursor.fetchall()
            for wc in watched_contents:
                src, c_id = wc["source"], wc["content_id"]
                try:
                    with self.db.get_content_connection(src, row_factory=True) as c_conn:
                        c_cursor = c_conn.cursor()
                        c_cursor.execute("SELECT genre FROM content_index WHERE id = ?", (c_id,))
                        c_row = c_cursor.fetchone()
                        if c_row and c_row["genre"]:
                            for g in c_row["genre"].split(","):
                                cleaned = g.strip()
                                if cleaned:
                                    genre_counts[cleaned] = genre_counts.get(cleaned, 0) + 1
                except Exception:
                    pass

            top_genres = sorted(genre_counts.items(), key=lambda x: x[1], reverse=True)[:5]

        hours_val = round(total_sec / 3600.0, 1)
        mins_val = int((total_sec % 3600) // 60)

        return {
            "status": "success",
            "user": user_info,
            "session": session_info,
            "stats": {
                "total_watch_hours": hours_val,
                "total_watch_minutes_str": f"{int(hours_val)} Std. {mins_val} Min.",
                "total_records": total_records,
                "completed_episodes": completed_cnt,
                "in_progress_episodes": in_progress_cnt,
                "completion_rate": round((completed_cnt / total_records * 100), 1) if total_records > 0 else 0,
                "watchlist_count": watchlist_cnt,
                "search_count": search_cnt,
                "favorite_source": favorite_source,
                "top_genres": top_genres,
                "sources_breakdown": source_breakdown,
                "daily_activity": {
                    "labels": daily_labels,
                    "hours": daily_hours
                },
                "hourly_activity": hourly_activity
            }
        }

    def get_user_watchlist_details(self, user_id: int) -> List[Dict[str, Any]]:
        results = []
        with self.db.get_user_connection(row_factory=True) as u_conn:
            u_cursor = u_conn.cursor()
            u_cursor.execute('''
                SELECT source, content_id, added_at
                FROM watchlist
                WHERE user_id = ?
                ORDER BY added_at DESC
            ''', (user_id,))
            rows = u_cursor.fetchall()

            for r in rows:
                src = r["source"]
                c_id = r["content_id"]
                item = {
                    "source": src,
                    "content_id": c_id,
                    "added_at": r["added_at"],
                    "title": f"Inhalt #{c_id}",
                    "cover_url": "",
                    "genre": "",
                    "description": ""
                }
                try:
                    with self.db.get_content_connection(src, row_factory=True) as c_conn:
                        c_cursor = c_conn.cursor()
                        c_cursor.execute("SELECT title, cover_url, genre, description FROM content_index WHERE id = ?", (c_id,))
                        c_row = c_cursor.fetchone()
                        if c_row:
                            item["title"] = c_row["title"]
                            item["cover_url"] = c_row["cover_url"]
                            item["genre"] = c_row["genre"]
                            item["description"] = c_row["description"]
                except Exception:
                    pass
                results.append(item)
        return results

    def get_user_search_history(self, user_id: int, page: int = 1, limit: int = 30) -> Dict[str, Any]:
        offset = (page - 1) * limit
        searches = []
        total_count = 0

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*) FROM search_logs WHERE user_id = ?", (user_id,))
            total_count = cursor.fetchone()[0]

            cursor.execute('''
                SELECT id, source, query, genre, results_count, timestamp
                FROM search_logs
                WHERE user_id = ?
                ORDER BY timestamp DESC
                LIMIT ? OFFSET ?
            ''', (user_id, limit, offset))
            searches = [dict(r) for r in cursor.fetchall()]

        return {
            "total": total_count,
            "page": page,
            "limit": limit,
            "searches": searches
        }

    @staticmethod
    def _quote_identifier(name: str) -> str:
        return '"' + name.replace('"', '""') + '"'

    @staticmethod
    def _list_tables(cursor) -> List[str]:
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
        return [r[0] for r in cursor.fetchall()]

    def get_db_tables(self, db_name: str) -> Dict[str, Any]:
        clean_db = self.db.sanitize_source(db_name) if db_name not in ["users", "user"] else "users"
        conn = self._get_db_conn(clean_db, row_factory=False)
        tables = []
        try:
            cursor = conn.cursor()
            for tbl in self._list_tables(cursor):
                cursor.execute(f"SELECT COUNT(*) FROM {self._quote_identifier(tbl)}")
                cnt = cursor.fetchone()[0]
                tables.append({"name": tbl, "count": cnt})
        finally:
            conn.close()
        return {"db_name": clean_db, "tables": tables}

    def get_table_data(self, db_name: str, table_name: str, page: int = 1, limit: int = 50, search: str = "") -> Dict[str, Any]:
        clean_db = self.db.sanitize_source(db_name) if db_name not in ["users", "user"] else "users"
        offset = (page - 1) * limit
        conn = self._get_db_conn(clean_db, row_factory=True)

        try:
            cursor = conn.cursor()
            if table_name not in self._list_tables(cursor):
                raise HTTPException(status_code=404, detail=f"Tabelle '{table_name}' existiert nicht")

            quoted_table = self._quote_identifier(table_name)
            cursor.execute(f"PRAGMA table_info({quoted_table})")
            schema = [dict(col) for col in cursor.fetchall()]
            col_names = [col["name"] for col in schema]

            sql = f"SELECT * FROM {quoted_table}"
            count_sql = f"SELECT COUNT(*) FROM {quoted_table}"
            params = []

            if search and col_names:
                text_cols = [c["name"] for c in schema if "CHAR" in c["type"].upper() or "TEXT" in c["type"].upper() or c["name"] in ["title", "username", "source"]]
                if not text_cols:
                    text_cols = col_names[:3]

                where_clauses = [f"{self._quote_identifier(col)} LIKE ?" for col in text_cols]
                sql += " WHERE " + " OR ".join(where_clauses)
                count_sql += " WHERE " + " OR ".join(where_clauses)
                params = [f"%{search}%"] * len(text_cols)

            cursor.execute(count_sql, tuple(params))
            total_count = cursor.fetchone()[0]

            sql += " LIMIT ? OFFSET ?"
            params.extend([limit, offset])

            cursor.execute(sql, tuple(params))
            rows = [dict(r) for r in cursor.fetchall()]

            return {
                "db_name": clean_db,
                "table_name": table_name,
                "schema": schema,
                "columns": col_names,
                "rows": rows,
                "total": total_count,
                "page": page,
                "limit": limit
            }
        finally:
            conn.close()

    def execute_sql_query(self, db_name: str, query: str) -> Dict[str, Any]:
        clean_db = self.db.sanitize_source(db_name) if db_name not in ["users", "user"] else "users"
        conn = self._get_db_conn(clean_db, row_factory=False)

        try:
            cursor = conn.cursor()
            cursor.execute(query)

            if cursor.description:
                columns = [desc[0] for desc in cursor.description]
                rows = [list(r) for r in cursor.fetchmany(200)]
                return {
                    "status": "success",
                    "columns": columns,
                    "rows": rows,
                    "row_count": len(rows)
                }
            else:
                conn.commit()
                return {
                    "status": "success",
                    "message": f"Query ausgeführt. Betroffene Zeilen: {cursor.rowcount}"
                }
        except Exception as e:
            return {"status": "error", "message": str(e)}
        finally:
            conn.close()
