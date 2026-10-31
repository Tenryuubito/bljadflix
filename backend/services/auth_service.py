import hmac
import json
import secrets
import sqlite3
import time
import urllib.request
from typing import Optional, List, Dict
from fastapi import HTTPException
from database import DatabaseManager
from passwords import verify_password
from models.auth import LoginRequest, CreateUserRequest, UpdateUserRequest


def resolve_ip_location(ip_address: Optional[str]) -> str:
    if not ip_address:
        return "Unbekannter Standort"

    ip_clean = ip_address.strip()
    if (
        ip_clean in ["127.0.0.1", "::1", "localhost"]
        or ip_clean.startswith("192.168.")
        or ip_clean.startswith("10.")
        or ip_clean.startswith("172.16.")
        or ip_clean.startswith("172.17.")
        or ip_clean.startswith("172.18.")
        or ip_clean.startswith("172.19.")
        or ip_clean.startswith("172.2")
        or ip_clean.startswith("172.30.")
        or ip_clean.startswith("172.31.")
    ):
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


class AuthService:
    def __init__(self, db: DatabaseManager):
        self.db = db

    def is_banned(self, ip_address: Optional[str], device_id: Optional[str]) -> bool:
        if not ip_address and not device_id:
            return False

        with self.db.get_user_connection(row_factory=True) as conn:
            cursor = conn.cursor()
            if device_id and ip_address:
                cursor.execute(
                    "SELECT id FROM banned_devices WHERE (device_id IS NOT NULL AND device_id = ?) OR ip_address = ?",
                    (device_id, ip_address)
                )
            elif device_id:
                cursor.execute(
                    "SELECT id FROM banned_devices WHERE device_id IS NOT NULL AND device_id = ?",
                    (device_id,)
                )
            else:
                cursor.execute(
                    "SELECT id FROM banned_devices WHERE ip_address = ?",
                    (ip_address,)
                )
            return cursor.fetchone() is not None

    def record_banned_attempt(self, ip_address: Optional[str], device_id: Optional[str]):
        if not ip_address and not device_id:
            return

        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            now = time.time()
            if device_id and ip_address:
                cursor.execute(
                    "UPDATE banned_devices SET last_attempt_at = ? WHERE (device_id IS NOT NULL AND device_id = ?) OR ip_address = ?",
                    (now, device_id, ip_address)
                )
            elif device_id:
                cursor.execute(
                    "UPDATE banned_devices SET last_attempt_at = ? WHERE device_id IS NOT NULL AND device_id = ?",
                    (now, device_id)
                )
            else:
                cursor.execute(
                    "UPDATE banned_devices SET last_attempt_at = ? WHERE ip_address = ?",
                    (now, ip_address)
                )
            conn.commit()

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
                    "3 fehlgeschlagene Anmeldeversuche"
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
            cursor.execute("SELECT * FROM banned_devices ORDER BY banned_at DESC")
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
                SELECT s.user_id, s.token, s.device_id, u.username, u.is_active
                FROM user_sessions s
                JOIN users u ON s.user_id = u.id
                WHERE s.token = ?
            ''', (token,))
            session = cursor.fetchone()

            if not session or not session["is_active"]:
                return None

            if x_device_id and session["device_id"] != x_device_id:
                raise HTTPException(
                    status_code=409,
                    detail="Konto wird bereits auf einem anderen Gerät genutzt."
                )

            conn.execute(
                "UPDATE user_sessions SET last_active_at = ? WHERE user_id = ?",
                (time.time(), session["user_id"])
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
                detail="Gerät ist dauerhaft gesperrt. Zugriff verweigert."
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
                    raise HTTPException(status_code=403, detail="3 fehlgeschlagene Anmeldeversuche. Dieses Gerät wurde dauerhaft gesperrt.")
                raise HTTPException(status_code=400, detail=f"Benutzername und Passwort erforderlich. Versuche: {fail_info['attempts']}/3")

            if not user or not user["is_active"]:
                fail_info = self.record_failed_login(client_ip, req.device_id, req.device_name, user_agent)
                if fail_info["banned"]:
                    raise HTTPException(
                        status_code=403,
                        detail="3 fehlgeschlagene Anmeldeversuche. Dieses Gerät wurde dauerhaft gesperrt."
                    )
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
            cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))
            conn.commit()
            return {"status": "success", "message": f"Benutzer '{user['username']}' gelöscht"}
