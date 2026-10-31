import hmac
import secrets
import threading
import time
from typing import Dict, Optional, Tuple

from config import Config

TOKEN_TTL_SECONDS = 12 * 3600
MAX_FAILED_ATTEMPTS = 5
LOCKOUT_SECONDS = 15 * 60


class AdminAuthService:
    def __init__(self, config: Config):
        self.config = config
        self.tokens: Dict[str, float] = {}
        self.failed_attempts: Dict[str, Tuple[int, float]] = {}
        self.lock = threading.Lock()

    def is_configured(self) -> bool:
        return bool(self.config.admin_password)

    def check_password(self, password: Optional[str]) -> bool:
        if not self.is_configured() or not password:
            return False
        return hmac.compare_digest(password.encode("utf-8"), self.config.admin_password.encode("utf-8"))

    def is_locked_out(self, client_ip: str) -> bool:
        with self.lock:
            count, last_at = self.failed_attempts.get(client_ip, (0, 0.0))
            if count >= MAX_FAILED_ATTEMPTS and time.time() - last_at < LOCKOUT_SECONDS:
                return True
            if count >= MAX_FAILED_ATTEMPTS:
                del self.failed_attempts[client_ip]
            return False

    def record_failure(self, client_ip: str) -> int:
        with self.lock:
            count, _ = self.failed_attempts.get(client_ip, (0, 0.0))
            self.failed_attempts[client_ip] = (count + 1, time.time())
            return count + 1

    def clear_failures(self, client_ip: str):
        with self.lock:
            self.failed_attempts.pop(client_ip, None)

    def create_token(self) -> str:
        token = secrets.token_hex(32)
        now = time.time()
        with self.lock:
            self.tokens = {t: exp for t, exp in self.tokens.items() if exp > now}
            self.tokens[token] = now + TOKEN_TTL_SECONDS
        return token

    def verify_token(self, token: Optional[str]) -> bool:
        if not token:
            return False
        with self.lock:
            expires_at = self.tokens.get(token)
            if expires_at is None:
                return False
            if expires_at <= time.time():
                del self.tokens[token]
                return False
            return True

    def revoke_token(self, token: Optional[str]):
        if not token:
            return
        with self.lock:
            self.tokens.pop(token, None)
