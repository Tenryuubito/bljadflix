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
from services.indexers.sto_indexer import StoIndexer

class TestStoIndexer(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db = DatabaseManager(db_dir=self.temp_dir.name)
        self.db.init_db(sources=["sto"])
        
        self.config_mock = MagicMock(spec=Config)
        self.config_mock.sources = {
            "sto": {"id": "sto", "name": "S.to", "url": "http://186.2.175.5"}
        }
        self.indexer = StoIndexer(self.db, self.config_mock)

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch.object(StoIndexer, 'fetch_with_retry')
    def test_process_single_series_sto(self, mock_fetch):
        detail_html = """
        <html>
            <head><title>Breaking Bad | S.to</title></head>
            <body>
                <h1>Breaking Bad</h1>
                <p class="serien-beschreibung">Chemistry teacher turns crystal meth producer</p>
                <div class="seriesCoverBox"><img src="/uploads/covers/bb.jpg" /></div>
                <div id="season-nav">
                    <a href="/serie/stream/breaking-bad/staffel-1">1</a>
                    <a href="/serie/stream/breaking-bad/filme">Filme</a>
                </div>
                <table class="episode-table">
                    <tr class="episode-row" onclick="window.location='/serie/stream/breaking-bad/staffel-1/episode-1'">
                        <td><strong class="episode-title-ger">Pilot</strong></td>
                    </tr>
                    <tr class="episode-row" onclick="window.location='/serie/stream/breaking-bad/staffel-1/episode-2'">
                        <td><strong class="episode-title-ger">Cat's in the Bag...</strong></td>
                    </tr>
                </table>
            </body>
        </html>
        """

        filme_html = """
        <html>
            <body>
                <table class="episode-table">
                    <tr class="episode-row" onclick="window.location='/serie/stream/breaking-bad/filme/film-1'">
                        <td><strong class="episode-title-ger">El Camino</strong></td>
                    </tr>
                </table>
            </body>
        </html>
        """

        def side_effect(url):
            res = MagicMock()
            if "filme" in url:
                res.text = filme_html
            else:
                res.text = detail_html
            return res

        mock_fetch.side_effect = side_effect

        s_url = "http://186.2.175.5/serie/stream/breaking-bad"
        url, success, msg = self.indexer.process_single_series(s_url)

        self.assertTrue(success)

        with self.db.get_content_connection("sto", row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM content_index WHERE source_page_url = ?", (s_url,))
            content = cursor.fetchone()
            self.assertIsNotNone(content)
            self.assertEqual(content["title"], "Breaking Bad")

            cursor.execute("SELECT * FROM episodes WHERE content_id = ? ORDER BY season_number, episode_number", (content["id"],))
            episodes = [dict(r) for r in cursor.fetchall()]

            self.assertEqual(len(episodes), 3)

            s0_eps = [e for e in episodes if e["season_number"] == 0]
            s1_eps = [e for e in episodes if e["season_number"] == 1]

            self.assertEqual(len(s0_eps), 1)
            self.assertEqual(s0_eps[0]["title"], "El Camino")

            self.assertEqual(len(s1_eps), 2)
            self.assertEqual(s1_eps[0]["title"], "Pilot")
            self.assertEqual(s1_eps[1]["title"], "Cat's in the Bag...")

if __name__ == "__main__":
    unittest.main()
