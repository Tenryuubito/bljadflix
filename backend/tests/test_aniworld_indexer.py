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
from services.indexers.aniworld_indexer import AniWorldIndexer

class TestAniWorldIndexer(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db = DatabaseManager(db_dir=self.temp_dir.name)
        self.db.init_db(sources=["aniworld"])
        
        self.config_mock = MagicMock(spec=Config)
        self.config_mock.sources = {
            "aniworld": {"id": "aniworld", "name": "AniWorld", "url": "https://aniworld.to"}
        }
        self.indexer = AniWorldIndexer(self.db, self.config_mock)

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch.object(AniWorldIndexer, 'fetch_with_retry')
    def test_process_single_series_with_seasons_and_episodes(self, mock_fetch):
        detail_html = """
        <html>
            <head><title>Attack on Titan - AniWorld</title></head>
            <body>
                <h1>Attack on Titan</h1>
                <span class="description-text">Humanity vs Giants</span>
                <div class="seriesCoverBox"><img src="/public/img/cover.jpg" /></div>
                <a href="/genre/action">Action</a>
                <div id="season-nav">
                    <a href="/anime/stream/attack-on-titan/staffel-1">Staffel 1</a>
                    <a href="/anime/stream/attack-on-titan/staffel-2">Staffel 2</a>
                    <a href="/anime/stream/attack-on-titan/filme">Filme</a>
                </div>
                <table class="episode-table">
                    <tr class="episode-row" onclick="window.location='/anime/stream/attack-on-titan/staffel-1/episode-1'">
                        <td><strong class="episode-title-ger">Die Erneuerung</strong></td>
                    </tr>
                    <tr class="episode-row" onclick="window.location='/anime/stream/attack-on-titan/staffel-1/episode-2'">
                        <td><strong class="episode-title-ger">Jener Tag</strong></td>
                    </tr>
                </table>
            </body>
        </html>
        """

        s2_html = """
        <html>
            <body>
                <table class="episode-table">
                    <tr class="episode-row" onclick="window.location='/anime/stream/attack-on-titan/staffel-2/episode-1'">
                        <td><strong class="episode-title-ger">Bestien-Titan</strong></td>
                    </tr>
                </table>
            </body>
        </html>
        """

        filme_html = """
        <html>
            <body>
                <table class="episode-table">
                    <tr class="episode-row" onclick="window.location='/anime/stream/attack-on-titan/filme/film-1'">
                        <td><strong class="episode-title-ger">Feuerroter Pfeil und Bogen</strong></td>
                    </tr>
                </table>
            </body>
        </html>
        """

        def side_effect(url):
            res = MagicMock()
            if "staffel-2" in url:
                res.text = s2_html
            elif "filme" in url:
                res.text = filme_html
            else:
                res.text = detail_html
            return res

        mock_fetch.side_effect = side_effect

        s_url = "https://aniworld.to/anime/stream/attack-on-titan"
        url, success, msg = self.indexer.process_single_series(s_url)

        self.assertTrue(success)
        self.assertIn("Episoden", msg)

        with self.db.get_content_connection("aniworld", row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM content_index WHERE source_page_url = ?", (s_url,))
            content = cursor.fetchone()
            self.assertIsNotNone(content)
            self.assertEqual(content["title"], "Attack on Titan")
            self.assertEqual(content["cover_url"], "https://aniworld.to/public/img/cover.jpg")

            cursor.execute("SELECT * FROM episodes WHERE content_id = ? ORDER BY season_number, episode_number", (content["id"],))
            episodes = [dict(r) for r in cursor.fetchall()]

            self.assertEqual(len(episodes), 4)

            s0_eps = [e for e in episodes if e["season_number"] == 0]
            s1_eps = [e for e in episodes if e["season_number"] == 1]
            s2_eps = [e for e in episodes if e["season_number"] == 2]

            self.assertEqual(len(s0_eps), 1)
            self.assertEqual(s0_eps[0]["title"], "Feuerroter Pfeil und Bogen")

            self.assertEqual(len(s1_eps), 2)
            self.assertEqual(s1_eps[0]["title"], "Die Erneuerung")
            self.assertEqual(s1_eps[1]["title"], "Jener Tag")

            self.assertEqual(len(s2_eps), 1)
            self.assertEqual(s2_eps[0]["title"], "Bestien-Titan")

    @patch.object(AniWorldIndexer, 'fetch_with_retry')
    def test_relative_url_joining(self, mock_fetch):
        detail_html = """
        <html>
            <head><title>Demo Anime</title></head>
            <body>
                <h1>Demo Anime</h1>
                <div id="season-nav">
                    <a href="staffel-1">Staffel 1</a>
                </div>
                <table class="episode-table">
                    <tr class="episode-row">
                        <td><a href="episode-1"><strong class="episode-title-ger">Episode One</strong></a></td>
                    </tr>
                </table>
            </body>
        </html>
        """
        res = MagicMock()
        res.text = detail_html
        mock_fetch.return_value = res

        s_url = "https://aniworld.to/anime/stream/demo-anime/"
        self.indexer.process_single_series(s_url)

        with self.db.get_content_connection("aniworld", row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT episode_url FROM episodes")
            row = cursor.fetchone()
            self.assertIsNotNone(row)
            self.assertEqual(row["episode_url"], "https://aniworld.to/anime/stream/demo-anime/episode-1")

if __name__ == "__main__":
    unittest.main()
