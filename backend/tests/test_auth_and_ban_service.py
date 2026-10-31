import os
import sys
import unittest
import uuid
from fastapi import HTTPException
from fastapi.testclient import TestClient

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

import tests
import app_backend
from models.auth import CreateUserRequest, LoginRequest
from passwords import hash_password


class TestAuthAndBanService(unittest.TestCase):

    def setUp(self):
        self.auth_service = app_backend.auth_service
        self.db = app_backend.db
        self.client = TestClient(app_backend.app)

        with self.db.get_user_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM banned_devices")
            cursor.execute("DELETE FROM login_attempts")
            conn.commit()

    def test_failed_login_attempts_and_automatic_ban(self):
        req = LoginRequest(
            username="WrongUser",
            password="WrongPassword",
            device_id="test_device_100",
            device_name="Test Device 100"
        )

        with self.assertRaises(HTTPException) as ctx1:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx1.exception.status_code, 400)
        self.assertIn("Versuche: 1/3", ctx1.exception.detail)
        self.assertFalse(self.auth_service.is_banned("192.168.1.100", "test_device_100"))

        with self.assertRaises(HTTPException) as ctx2:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx2.exception.status_code, 400)
        self.assertIn("Versuche: 2/3", ctx2.exception.detail)
        self.assertFalse(self.auth_service.is_banned("192.168.1.100", "test_device_100"))

        with self.assertRaises(HTTPException) as ctx3:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx3.exception.status_code, 403)
        self.assertIn("3 fehlgeschlagene Anmeldeversuche", ctx3.exception.detail)

        self.assertTrue(self.auth_service.is_banned("192.168.1.100", "test_device_100"))

        with self.assertRaises(HTTPException) as ctx4:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx4.exception.status_code, 403)
        self.assertIn("dauerhaft gesperrt", ctx4.exception.detail)

    def test_unban_device(self):
        self.auth_service.ban_device_manually(
            ip_address="10.0.0.50",
            device_id="test_device_50",
            device_name="Bad Device",
            reason="Test Sperre"
        )
        self.assertTrue(self.auth_service.is_banned("10.0.0.50", "test_device_50"))

        banned_list = self.auth_service.get_banned_devices()
        self.assertEqual(len(banned_list), 1)
        banned_id = banned_list[0]["id"]

        unban_res = self.auth_service.unban_device(banned_id)
        self.assertEqual(unban_res["status"], "success")
        self.assertFalse(self.auth_service.is_banned("10.0.0.50", "test_device_50"))

    def test_middleware_blocks_banned_device_http_requests(self):
        self.auth_service.ban_device_manually(
            ip_address="203.0.113.199",
            device_id="device_999",
            device_name="Hacker TV",
            reason="3 fehlgeschlagene Anmeldeversuche"
        )

        resp = self.client.get(
            "/movies",
            headers={"X-Forwarded-For": "203.0.113.199", "X-Device-ID": "device_999"}
        )
        self.assertEqual(resp.status_code, 403)
        self.assertIn("dauerhaft gesperrt", resp.json()["detail"])

    def _create_user(self, login_code: str) -> dict:
        user = self.auth_service.create_user(CreateUserRequest(username=f"user_{uuid.uuid4().hex[:8]}", login_code=login_code))
        self.addCleanup(self.auth_service.delete_user, user["id"])
        return user

    def test_login_with_username_only_is_rejected(self):
        user = self._create_user(f"{uuid.uuid4().int % 900000 + 100000}")
        req = LoginRequest(username=user["username"], device_id="device_username_only")

        with self.assertRaises(HTTPException) as ctx:
            self.auth_service.login(req, client_ip="192.168.1.150")
        self.assertEqual(ctx.exception.status_code, 400)

    def test_login_with_username_and_login_code_succeeds(self):
        code = f"{uuid.uuid4().int % 900000 + 100000}"
        user = self._create_user(code)
        req = LoginRequest(username=user["username"], password=code, device_id="device_code_login")

        res = self.auth_service.login(req, client_ip="192.168.1.151")
        self.assertEqual(res["user_id"], user["id"])
        self.assertTrue(res["token"])

    def test_login_verifies_hashed_password(self):
        user = self._create_user(f"{uuid.uuid4().int % 900000 + 100000}")
        with self.db.get_user_connection() as conn:
            conn.execute("UPDATE users SET password = ? WHERE id = ?", (hash_password("geheim123"), user["id"]))
            conn.commit()

        ok = self.auth_service.login(
            LoginRequest(username=user["username"], password="geheim123", device_id="device_hash_ok"),
            client_ip="192.168.1.152"
        )
        self.assertEqual(ok["user_id"], user["id"])

        with self.assertRaises(HTTPException) as ctx:
            self.auth_service.login(
                LoginRequest(username=user["username"], password="falsch", device_id="device_hash_bad"),
                client_ip="192.168.1.153"
            )
        self.assertEqual(ctx.exception.status_code, 400)

    def test_admin_endpoints_require_admin_token(self):
        self.assertEqual(self.client.get("/admin/api/stats").status_code, 401)
        self.assertEqual(self.client.post("/admin/api/db/query", json={"db_name": "users", "query": "SELECT 1"}).status_code, 401)
        self.assertEqual(self.client.get("/admin/users").status_code, 401)

        wrong = self.client.post("/admin/api/login", json={"password": "falsch"})
        self.assertEqual(wrong.status_code, 401)

        login = self.client.post("/admin/api/login", json={"password": tests.TEST_ADMIN_PASSWORD})
        self.assertEqual(login.status_code, 200)
        token = login.json()["token"]

        resp = self.client.get("/admin/api/stats", headers={"X-Admin-Token": token})
        self.assertEqual(resp.status_code, 200)

        self.client.post("/admin/api/logout", headers={"X-Admin-Token": token})
        self.assertEqual(self.client.get("/admin/api/stats", headers={"X-Admin-Token": token}).status_code, 401)

    def test_trigger_index_requires_authentication(self):
        resp = self.client.post("/admin/trigger-index?source=aniworld")
        self.assertEqual(resp.status_code, 401)

    def test_install_shortcut_redirects_to_launcher_download(self):
        resp = self.client.get("/install", follow_redirects=False)
        self.assertEqual(resp.status_code, 307)
        self.assertEqual(resp.headers["location"], "/app/download/launcher")

    def test_unauthenticated_api_access_is_blocked(self):
        resp = self.client.get("/movies")
        self.assertEqual(resp.status_code, 401)

        resp = self.client.get("/genres")
        self.assertEqual(resp.status_code, 401)

        resp = self.client.get("/search?query=test")
        self.assertEqual(resp.status_code, 401)


if __name__ == "__main__":
    unittest.main()
