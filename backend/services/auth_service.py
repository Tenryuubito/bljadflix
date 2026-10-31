import hmac
import ipaddress
import json
import secrets
import sqlite3
import time
import urllib.request
from typing import Optional, List, Dict
from fastapi import HTTPException
from config import Config
from database import DatabaseManager
from passwords import verify_password
from models.auth import LoginRequest, CreateUserRequest, UpdateUserRequest


def resolve_ip_location(ip_address: Optional[str]) -> str:
    if not ip_address:
        return "Unbekannter Standort"

    ip_clean = ip_address.strip()
    if ip_clean == "localhost":
        return "Lokales Netzwerk (LAN / Loopback)"
    try:
        parsed = ipaddress.ip_address(ip_clean)
    except ValueError:
        return "Unbekannter Standort"
    if parsed.is_private or parsed.is_loopback:
        return "Lokales Netzwerk (LAN / Loopback)"

    try:
        url = f"http://ip-api.com/json/{ip_clean}?fields=status,country,city,isp"
        req = urllib.request.Request(url, headers={"User-Agent": "Bljadflix/1.0"})
        with urllib.request.urlopen(req, timeout=2.0) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            if data.get("status") == "success":
                country = data.get("country", "")
                city = data.get("city", "")
                isp = data.get("isp", "")
                parts = [p for p in [country, city, f"({isp})" if isp else ""] if p]
                return ", ".join(parts) if parts else "Unbekannter Standort"
    except Exception:
        pass
    return "Unbekannter Standort"


AUTO_BAN_REASON = "3 fehlgeschlagene Anmeldeversuche"
SESSION_ACTIVITY_INTERVAL_SECONDS = 60


class AuthService:
    def __init__(self, db: DatabaseManager, config: Optional[Config] = None):
        self.db = db
        self.auto_ban_hours = config.auto_ban_hours if config else 24.0

    def _active_ban_filter(self) -> tuple:
        return "(reason != ? OR banned_at > ?)", [AUTO_BAN_REASON, time.time() - self.auto_ban_hours * 3600]

    def _ban_match(self, ip_address: Optional[str], device_id: Optional[str]) -> tuple:
        conditions = []
        params = []
        if device_id:
            conditions.append("(device_id IS NOT NULL AND device_id = ?)")
            params.append(device_id)
        if ip_address:
            conditions.append("ip_address = ?")
            params.append(ip_address)
        active_sql, active_params = self._active_ban_filter()
        return f"({' OR '.join(conditions)}) AND {active_sql}", params + active_params

    def is_banned(self, ip_address: Optional[str], device_id: Optional[str]) -> bool:
        if not ip_address and not device_id:
            return False

        where, params = self._ban_match(ip_address, device_id)
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute(f"SELECT id FROM banned_devices WHERE {where} LIMIT 1", params)
            return cursor.fetchone() is not None

    def record_banned_attempt(self, ip_address: Optional[str], device_id: Optional[str]):
        if not ip_address and not device_id:
            return

        where, params = self._ban_match(ip_address, device_id)
        with self.db.get_user_connection() as conn:
            conn.execute(f"UPDATE banned_devices SET last_attempt_at = ? WHERE {where}", [time.time()] + params)
            conn.commit()

    def _ban_message(self) -> str:
        hours = self.auto_ban_hours
        duration = f"{int(hours)} Stunden" if hours == int(hours) else f"{hours} Stunden"
        return f"{AUTO_BAN_REASON}. Dieses Gerät wurde für {duration} gesperrt."

    def record_failed_login(
        self,
        ip_address: str,
        device_id: Optional[str],
        device_name: Optional[str] = "Unbekanntes Gerät",
        user_agent: Optional[str] = ""
    ) -> Dict:
        now = time.time()
        dev_id = device_id or "unknown_device"

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT attempt_count FROM login_attempts WHERE device_id = ? OR ip_address = ?",
                (dev_id, ip_address)
            )
            row = cursor.fetchone()
            current_attempts = row["attempt_count"] if row else 0
            new_attempts = current_attempts + 1

            if new_attempts >= 3:
                location = resolve_ip_location(ip_address)
                cursor.execute('''
                    INSERT INTO banned_devices (device_id, ip_address, device_name, user_agent, location, banned_at, failed_attempts, last_attempt_at, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ''', (
                    device_id,
                    ip_address,
                    device_name or "Unbekanntes Gerät",
                    user_agent or "",
                    location,
                    now,
                    new_attempts,
                    now,
                    AUTO_BAN_REASON
                ))

                cursor.execute(
                    "DELETE FROM login_attempts WHERE device_id = ? OR ip_address = ?",
                    (dev_id, ip_address)
                )
                conn.commit()
                return {"banned": True, "attempts": new_attempts, "remaining": 0}
            else:
                cursor.execute('''
                    INSERT INTO login_attempts (device_id, ip_address, attempt_count, last_attempt_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(device_id, ip_address) DO UPDATE SET
                        attempt_count = excluded.attempt_count,
                        last_attempt_at = excluded.last_attempt_at
                ''', (dev_id, ip_address, new_attempts, now))
                conn.commit()
                return {"banned": False, "attempts": new_attempts, "remaining": 3 - new_attempts}

    def clear_failed_attempts(self, ip_address: str, device_id: Optional[str]):
        dev_id = device_id or "unknown_device"
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "DELETE FROM login_attempts WHERE device_id = ? OR ip_address = ?",
                (dev_id, ip_address)
            )
            conn.commit()

    def get_banned_devices(self) -> List[Dict]:
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            active_sql, active_params = self._active_ban_filter()
            cursor.execute(f"SELECT * FROM banned_devices WHERE {active_sql} ORDER BY banned_at DESC", active_params)
            return [dict(row) for row in cursor.fetchall()]

    def unban_device(self, banned_id: int) -> Dict:
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM banned_devices WHERE id = ?", (banned_id,))
            conn.commit()
        return {"status": "success", "message": "Gerät erfolgreich entsperrt"}

    def ban_device_manually(
        self,
        ip_address: str,
        device_id: Optional[str] = None,
        device_name: Optional[str] = "Manuell gesperrt",
        reason: Optional[str] = "Manuelle Sperre durch Administrator"
    ) -> Dict:
        now = time.time()
        location = resolve_ip_location(ip_address)
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                INSERT INTO banned_devices (device_id, ip_address, device_name, user_agent, location, banned_at, failed_attempts, last_attempt_at, reason)
                VALUES (?, ?, ?, ?, ?, ?, 3, ?, ?)
            ''', (
                device_id,
                ip_address.strip(),
                device_name or "Manuell gesperrt",
                "Manuelle Sperre",
                location,
                now,
                now,
                reason or "Manuelle Sperre durch Administrator"
            ))
            conn.commit()
        return {"status": "success", "message": f"Gerät/IP '{ip_address}' wurde manuell gesperrt"}

    def get_current_user_optional(self, authorization: Optional[str], x_device_id: Optional[str]) -> Optional[Dict]:
        if not authorization or not authorization.startswith("Bearer "):
            return None

        token = authorization.split(" ", 1)[1]
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT s.user_id, s.token, s.device_id, s.last_active_at, u.username, u.is_active
                FROM user_sessions s
                JOIN users u ON s.user_id = u.id
                WHERE s.token = ?
            ''', (token,))
            session = cursor.fetchone()

            if not session or not session["is_active"]:
                return None

            if session["device_id"] and session["device_id"] != x_device_id:
                raise HTTPException(
                    status_code=409,
                    detail="Konto wird bereits auf einem anderen Gerät genutzt."
                )

            now = time.time()
            if now - (session["last_active_at"] or 0) >= SESSION_ACTIVITY_INTERVAL_SECONDS:
                conn.execute(
                    "UPDATE user_sessions SET last_active_at = ? WHERE user_id = ?",
                    (now, session["user_id"])
                )
                conn.commit()

            return {
                "user_id": session["user_id"],
                "username": session["username"],
                "device_id": session["device_id"]
            }

    def get_current_user(self, authorization: Optional[str], x_device_id: Optional[str]) -> Dict:
        user = self.get_current_user_optional(authorization, x_device_id)
        if not user:
            raise HTTPException(
                status_code=401,
                detail="Nicht angemeldet oder ungültige Sitzung."
            )
        return user

    def login(self, req: LoginRequest, client_ip: str = "127.0.0.1", user_agent: str = "") -> Dict:
        if self.is_banned(client_ip, req.device_id):
            self.record_banned_attempt(client_ip, req.device_id)
            raise HTTPException(
                status_code=403,
                detail="Gerät ist gesperrt. Zugriff verweigert."
            )

        username = req.username.strip() if req.username else None
        password = req.password.strip() if req.password else None
        code = req.code.strip() if req.code else None

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()

            user = None
            if username and password:
                cursor.execute(
                    "SELECT id, username, is_active, password, login_code FROM users WHERE LOWER(username) = LOWER(?)",
                    (username,)
                )
                candidate = cursor.fetchone()
                if candidate and (
                    verify_password(password, candidate["password"])
                    or hmac.compare_digest(password.encode("utf-8"), str(candidate["login_code"]).encode("utf-8"))
                ):
                    user = candidate
            elif code and not username:
                cursor.execute(
                    "SELECT id, username, is_active FROM users WHERE login_code = ?",
                    (code,)
                )
                user = cursor.fetchone()
            else:
                fail_info = self.record_failed_login(client_ip, req.device_id, req.device_name, user_agent)
                if fail_info["banned"]:
                    raise HTTPException(status_code=403, detail=self._ban_message())
                raise HTTPException(status_code=400, detail=f"Benutzername und Passwort erforderlich. Versuche: {fail_info['attempts']}/3")

            if not user or not user["is_active"]:
                fail_info = self.record_failed_login(client_ip, req.device_id, req.device_name, user_agent)
                if fail_info["banned"]:
                    raise HTTPException(status_code=403, detail=self._ban_message())
                raise HTTPException(
                    status_code=400,
                    detail=f"Ungültige Anmeldedaten. Versuche: {fail_info['attempts']}/3. Verbleibende Versuche: {fail_info['remaining']}"
                )

            self.clear_failed_attempts(client_ip, req.device_id)

            user_id = user["id"]
            token = secrets.token_hex(20)
            current_time = time.time()

            cursor.execute('''
                INSERT INTO user_sessions (user_id, token, device_id, device_name, logged_in_at, last_active_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    token = excluded.token,
                    device_id = excluded.device_id,
                    device_name = excluded.device_name,
                    logged_in_at = excluded.logged_in_at,
                    last_active_at = excluded.last_active_at
            ''', (user_id, token, req.device_id, req.device_name, current_time, current_time))
            conn.commit()

            return {
                "token": token,
                "user_id": user_id,
                "username": user["username"],
                "device_id": req.device_id
            }

    def logout(self, user_id: int) -> dict:
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM user_sessions WHERE user_id = ?", (user_id,))
            conn.commit()
        return {"status": "success"}

    def create_user(self, req: CreateUserRequest) -> Dict:
        code = req.login_code.strip() if req.login_code else str(secrets.randbelow(899999) + 100000)
        current_time = time.time()
        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            try:
                cursor.execute('''
                    INSERT INTO users (username, login_code, created_at, is_active)
                    VALUES (?, ?, ?, 1)
                ''', (req.username.strip(), code, current_time))
                conn.commit()
                new_id = cursor.lastrowid
                return {
                    "id": new_id,
                    "username": req.username.strip(),
                    "login_code": code,
                    "created_at": current_time,
                    "is_active": True,
                    "active_device_id": None
                }
            except sqlite3.IntegrityError:
                raise HTTPException(status_code=400, detail="Benutzername oder Anmeldecode existiert bereits")

    def list_users(self) -> List[Dict]:
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT u.*, s.device_id as active_device_id
                FROM users u
                LEFT JOIN user_sessions s ON u.id = s.user_id
                ORDER BY u.id ASC
            ''')
            return [dict(row) for row in cursor.fetchall()]

    def update_user(self, req: UpdateUserRequest) -> Dict:
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id FROM users WHERE id = ?", (req.user_id,))
            if not cursor.fetchone():
                raise HTTPException(status_code=404, detail="Benutzer nicht gefunden")

            try:
                cursor.execute('''
                    UPDATE users
                    SET username = ?, login_code = ?, is_active = ?
                    WHERE id = ?
                ''', (req.username.strip(), req.login_code.strip(), int(req.is_active), req.user_id))
                conn.commit()
                return {"status": "success", "message": "Benutzer erfolgreich aktualisiert"}
            except sqlite3.IntegrityError:
                raise HTTPException(status_code=400, detail="Benutzername oder Anmeldecode wird bereits verwendet")

    def delete_user(self, user_id: int) -> Dict:
        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, username FROM users WHERE id = ?", (user_id,))
            user = cursor.fetchone()
            if not user:
                raise HTTPException(status_code=404, detail="Benutzer nicht gefunden")

            cursor.execute("DELETE FROM user_sessions WHERE user_id = ?", (user_id,))
            cursor.execute("DELETE FROM watch_progress WHERE user_id = ?", (user_id,))
            cursor.execute("DELETE FROM watchlist WHERE user_id = ?", (user_id,))
            cursor.execute("DELETE FROM watch_logs WHERE user_id = ?", (user_id,))
            cursor.execute("DELETE FROM hidden_continue_watching WHERE user_id = ?", (user_id,))
            cursor.execute("DELETE FROM search_logs WHERE user_id = ?", (user_id,))
            cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))
            conn.commit()
            return {"status": "success", "message": f"Benutzer '{user['username']}' gelöscht"}
