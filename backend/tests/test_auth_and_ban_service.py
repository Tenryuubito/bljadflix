import os
import sys
import unittest
from fastapi import HTTPException
from fastapi.testclient import TestClient

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

import app_backend
from models.auth import LoginRequest


class TestAuthAndBanService(unittest.TestCase):

    def setUp(self):
        self.auth_service = app_backend.auth_service
        self.db = app_backend.db
        self.client = TestClient(app_backend.app)

        # Clear banned_devices and login_attempts before each test
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

        # Attempt 1 -> Fail
        with self.assertRaises(HTTPException) as ctx1:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx1.exception.status_code, 400)
        self.assertIn("Versuche: 1/3", ctx1.exception.detail)
        self.assertFalse(self.auth_service.is_banned("192.168.1.100", "test_device_100"))

        # Attempt 2 -> Fail
        with self.assertRaises(HTTPException) as ctx2:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx2.exception.status_code, 400)
        self.assertIn("Versuche: 2/3", ctx2.exception.detail)
        self.assertFalse(self.auth_service.is_banned("192.168.1.100", "test_device_100"))

        # Attempt 3 -> Permanent Ban!
        with self.assertRaises(HTTPException) as ctx3:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx3.exception.status_code, 403)
        self.assertIn("3 fehlgeschlagene Anmeldeversuche", ctx3.exception.detail)

        # Device/IP should now be permanently banned
        self.assertTrue(self.auth_service.is_banned("192.168.1.100", "test_device_100"))

        # Attempt 4 when banned -> Blocked directly with 403
        with self.assertRaises(HTTPException) as ctx4:
            self.auth_service.login(req, client_ip="192.168.1.100")
        self.assertEqual(ctx4.exception.status_code, 403)
        self.assertIn("dauerhaft gesperrt", ctx4.exception.detail)

    def test_unban_device(self):
        # Manually ban a device
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

        # Unban
        unban_res = self.auth_service.unban_device(banned_id)
        self.assertEqual(unban_res["status"], "success")
        self.assertFalse(self.auth_service.is_banned("10.0.0.50", "test_device_50"))

    def test_middleware_blocks_banned_device_http_requests(self):
        # Ban device_999
        self.auth_service.ban_device_manually(
            ip_address="203.0.113.199",
            device_id="device_999",
            device_name="Hacker TV",
            reason="3 fehlgeschlagene Anmeldeversuche"
        )

        # Send request with banned headers
        resp = self.client.get(
            "/movies",
            headers={"X-Forwarded-For": "203.0.113.199", "X-Device-ID": "device_999"}
        )
        self.assertEqual(resp.status_code, 403)
        self.assertIn("dauerhaft gesperrt", resp.json()["detail"])

    def test_unauthenticated_api_access_is_blocked(self):
        # Send request without Bearer token from clean IP
        resp = self.client.get("/movies")
        self.assertEqual(resp.status_code, 401)

        resp = self.client.get("/genres")
        self.assertEqual(resp.status_code, 401)

        resp = self.client.get("/search?query=test")
        self.assertEqual(resp.status_code, 401)


if __name__ == "__main__":
    unittest.main()
