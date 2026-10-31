import os
import sys
import time
import unittest
from unittest.mock import patch

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

import tests
import app_backend
from models.content import ProgressRequest


class TestContinueWatchingHide(unittest.TestCase):

    USER_ID = 4242

    def setUp(self):
        self.service = app_backend.content_service
        with app_backend.db.get_content_connection("sto") as conn:
            cursor = conn.cursor()
            cursor.execute(
                "INSERT INTO content_index (title, description, cover_url, source_page_url, genre) VALUES (?, ?, ?, ?, ?)",
                ("Testserie", "Beschreibung", "", f"https://example.test/serie/{time.time_ns()}", "Drama")
            )
            self.content_id = cursor.lastrowid
            cursor.execute(
                "INSERT INTO episodes (content_id, season_number, episode_number, title, episode_url) VALUES (?, 1, 1, 'Folge 1', 'u1')",
                (self.content_id,)
            )
            self.episode_id = cursor.lastrowid
            conn.commit()

    def tearDown(self):
        with app_backend.db.get_user_connection() as conn:
            conn.execute("DELETE FROM watch_progress WHERE user_id = ?", (self.USER_ID,))
            conn.execute("DELETE FROM watch_logs WHERE user_id = ?", (self.USER_ID,))
            conn.execute("DELETE FROM hidden_continue_watching WHERE user_id = ?", (self.USER_ID,))
            conn.commit()

    def _watch(self):
        self.service.update_progress(
            user_id=self.USER_ID,
            source="sto",
            req=ProgressRequest(
                episode_id=self.episode_id,
                content_id=self.content_id,
                progress_seconds=120,
                duration_seconds=1400,
                is_completed=False,
                source="sto"
            )
        )

    def _continue_ids(self):
        return [item["id"] for item in self.service.get_continue_watching(user_id=self.USER_ID, source="sto")]

    def test_hidden_series_disappears_from_continue_watching(self):
        self._watch()
        self.assertIn(self.content_id, self._continue_ids())

        res = app_backend.hide_continue_watching(content_id=self.content_id, source="sto", user={"user_id": self.USER_ID})

        self.assertEqual(res, {"status": "success"})
        self.assertNotIn(self.content_id, self._continue_ids())

    def test_watching_again_brings_series_back(self):
        with patch("services.content_service.time.time", return_value=1000.0):
            self._watch()
            self.service.hide_continue_watching(user_id=self.USER_ID, source="sto", content_id=self.content_id)
        self.assertNotIn(self.content_id, self._continue_ids())

        with patch("services.content_service.time.time", return_value=2000.0):
            self._watch()

        self.assertIn(self.content_id, self._continue_ids())

    def test_hiding_keeps_episode_progress(self):
        self._watch()
        self.service.hide_continue_watching(user_id=self.USER_ID, source="sto", content_id=self.content_id)

        episodes = self.service.get_episodes(source="sto", content_id=self.content_id, user_id=self.USER_ID)

        self.assertEqual(episodes[1][0]["progress_seconds"], 120.0)


if __name__ == "__main__":
    unittest.main()
