import hashlib
import hmac
import secrets
from typing import Optional

ALGORITHM = "pbkdf2_sha256"
ITERATIONS = 260000


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), ITERATIONS).hex()
    return f"{ALGORITHM}${ITERATIONS}${salt}${digest}"


def is_hashed(value: Optional[str]) -> bool:
    return bool(value) and value.startswith(f"{ALGORITHM}$")


def verify_password(password: str, stored: Optional[str]) -> bool:
    if not password or not is_hashed(stored):
        return False
    try:
        _, iterations, salt, digest = stored.split("$", 3)
        candidate = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), int(iterations)).hex()
    except ValueError:
        return False
    return hmac.compare_digest(candidate, digest)
