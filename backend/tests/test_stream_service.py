import os
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from database import DatabaseManager
from config import Config
from services.stream_service import StreamService

class TestStreamService(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db = DatabaseManager(db_dir=self.temp_dir.name)
        self.db.init_db(sources=["aniworld", "sto", "filmo"])
        
        self.config_mock = MagicMock(spec=Config)
        self.config_mock.sources = {
            "aniworld": {"id": "aniworld", "name": "AniWorld", "url": "https://aniworld.to"},
            "sto": {"id": "sto", "name": "S.to", "url": "http://186.2.175.5"},
            "filmo": {"id": "filmo", "name": "Filmo", "url": "https://filmo.to"}
        }
        self.stream_service = StreamService(self.db, self.config_mock)

        with self.db.get_content_connection("aniworld") as conn:
            cursor = conn.cursor()
            cursor.execute("INSERT INTO content_index (id, title, source_page_url) VALUES (1, 'Test Show', 'https://aniworld.to/anime/stream/test-show')")
            cursor.execute("INSERT INTO episodes (id, content_id, season_number, episode_number, title, episode_url) VALUES (10, 1, 1, 1, 'Ep 1', 'https://aniworld.to/anime/stream/test-show/staffel-1/episode-1')")
            conn.commit()

        with self.db.get_content_connection("filmo") as conn:
            cursor = conn.cursor()
            cursor.execute("INSERT INTO content_index (id, title, source_page_url) VALUES (1, 'Test Film', 'https://filmo.to/movies/test-film')")
            cursor.execute("INSERT INTO episodes (id, content_id, season_number, episode_number, title, episode_url) VALUES (20, 1, 1, 1, 'Test Film', 'https://filmo.to/movies/test-film')")
            conn.commit()

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch('requests.Session')
    def test_resolve_stream_aniworld_with_redirect(self, mock_session_cls):
        mock_session = MagicMock()
        mock_session_cls.return_value = mock_session

        ep_page_res = MagicMock()
        ep_page_res.status_code = 200
        ep_page_res.text = """
        <html>
            <body>
                <ul class="hosterSiteDirectNav">
                    <li data-link-target="/redirect/123456" class="generateUrl">VOE</li>
                </ul>
            </body>
        </html>
        """

        redir_res = MagicMock()
        redir_res.status_code = 200
        redir_res.url = "https://voe.sx/e/sample123"

        hoster_page_res = MagicMock()
        hoster_page_res.status_code = 200
        hoster_page_res.text = "var sources = { file: 'https://cdn.voe-network.net/engine/hls2/manifest.m3u8' };"

        def get_side_effect(url, **kwargs):
            if "/redirect/" in url:
                return redir_res
            elif "voe.sx" in url:
                return hoster_page_res
            else:
                return ep_page_res

        mock_session.get.side_effect = get_side_effect

        result = self.stream_service.resolve_stream(episode_id=10, source="aniworld")

        self.assertIn("stream_url", result)
        self.assertEqual(result["stream_url"], "https://cdn.voe-network.net/engine/hls2/manifest.m3u8")

    @patch('requests.Session')
    def test_resolve_stream_filmo_open_mint(self, mock_session_cls):
        mock_session = MagicMock()
        mock_session_cls.return_value = mock_session

        ep_page_res = MagicMock()
        ep_page_res.status_code = 200
        ep_page_res.text = """
        <html>
            <body>
                <div data-provider-chip="true" data-p="encrypted_payload_123">Play</div>
            </body>
        </html>
        """

        mint_post_res = MagicMock()
        mint_post_res.status_code = 200
        mint_post_res.json.return_value = {"x": "token_abc_456"}

        embed_res = MagicMock()
        embed_res.status_code = 200
        embed_res.url = "https://filmo.to/embed/player_token_abc_456.mp4"

        mock_session.get.side_effect = [ep_page_res, embed_res]
        mock_session.post.return_value = mint_post_res

        result = self.stream_service.resolve_stream(episode_id=20, source="filmo")

        self.assertIn("stream_url", result)
        self.assertEqual(result["stream_url"], "https://filmo.to/embed/player_token_abc_456.mp4")

if __name__ == "__main__":
    unittest.main()
