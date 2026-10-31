import os
import sys
import unittest
from unittest.mock import patch

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

import tests
import app_backend


class TestBackendReindex(unittest.TestCase):

    @patch.object(app_backend.indexer_service, 'process_single_series_background')
    @patch.object(app_backend.content_service, 'get_source_page_url')
    @patch.object(app_backend.content_service, 'get_episodes')
    def test_get_episodes_triggers_background_reindex(self, mock_get_episodes, mock_get_url, mock_process_bg):
        mock_get_url.return_value = "https://aniworld.to/anime/stream/test-anime"
        mock_get_episodes.return_value = {"1": [{"id": 1, "episode_number": 1, "title": "Ep 1"}]}

        mock_bg_tasks = unittest.mock.MagicMock()
        res = app_backend.get_episodes(content_id=10, background_tasks=mock_bg_tasks, source="aniworld", user={"user_id": 1})

        mock_get_url.assert_called_once_with(source="aniworld", content_id=10)
        mock_bg_tasks.add_task.assert_called_once_with(
            app_backend.indexer_service.process_single_series_background,
            "https://aniworld.to/anime/stream/test-anime",
            "aniworld"
        )
        mock_get_episodes.assert_called_once_with(source="aniworld", content_id=10, user_id=1)
        self.assertEqual(res, {"1": [{"id": 1, "episode_number": 1, "title": "Ep 1"}]})

    @patch.object(app_backend.indexer_service, 'process_single_series_background')
    @patch.object(app_backend.content_service, 'get_source_page_url')
    def test_reindex_series_endpoint_success(self, mock_get_url, mock_process_bg):
        mock_get_url.return_value = "https://aniworld.to/anime/stream/test-anime"

        mock_bg_tasks = unittest.mock.MagicMock()
        res = app_backend.reindex_single_series(content_id=10, background_tasks=mock_bg_tasks, source="aniworld")

        self.assertEqual(res, {"status": "success", "message": "Reindexierung im Hintergrund gestartet"})
        mock_get_url.assert_called_once_with(source="aniworld", content_id=10)
        mock_bg_tasks.add_task.assert_called_once_with(
            app_backend.indexer_service.process_single_series_background,
            "https://aniworld.to/anime/stream/test-anime",
            "aniworld",
            True
        )


class TestSeriesRefreshThrottle(unittest.TestCase):

    def setUp(self):
        self.service = app_backend.indexer_service
        self.service.last_refreshed.clear()
        self.url = "https://aniworld.to/anime/stream/drossel-test"

    @patch.object(app_backend.indexer_service, 'process_single_series')
    def test_background_refresh_is_throttled(self, mock_process):
        mock_process.return_value = (self.url, True, "ok")

        self.service.process_single_series_background(self.url, "aniworld")
        self.service.process_single_series_background(self.url, "aniworld")

        self.assertEqual(mock_process.call_count, 1)

    @patch.object(app_backend.indexer_service, 'process_single_series')
    def test_forced_refresh_ignores_throttle(self, mock_process):
        mock_process.return_value = (self.url, True, "ok")

        self.service.process_single_series_background(self.url, "aniworld")
        self.service.process_single_series_background(self.url, "aniworld", True)

        self.assertEqual(mock_process.call_count, 2)

    @patch("services.indexer_service.time.time")
    @patch.object(app_backend.indexer_service, 'process_single_series')
    def test_refresh_runs_again_after_interval(self, mock_process, mock_time):
        mock_process.return_value = (self.url, True, "ok")
        mock_time.return_value = 1000.0
        self.service.process_single_series_background(self.url, "aniworld")
        mock_time.return_value = 1000.0 + 6 * 3600 + 1
        self.service.process_single_series_background(self.url, "aniworld")

        self.assertEqual(mock_process.call_count, 2)


if __name__ == "__main__":
    unittest.main()
