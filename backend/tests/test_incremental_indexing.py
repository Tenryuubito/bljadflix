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
from services.indexers.filmo_indexer import FilmoIndexer
from services.indexers.sto_indexer import StoIndexer

class TestIncrementalIndexing(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db = DatabaseManager(db_dir=self.temp_dir.name)
        self.db.init_db(sources=["aniworld", "filmo", "sto"])
        
        self.config_mock = MagicMock(spec=Config)
        self.config_mock.sources = {
            "aniworld": {"id": "aniworld", "name": "AniWorld", "url": "https://aniworld.to"},
            "filmo": {"id": "filmo", "name": "Filmo", "url": "https://filmo.to"},
            "sto": {"id": "sto", "name": "S.to", "url": "http://186.2.175.5"}
        }

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch.object(AniWorldIndexer, 'process_single_series')
    @patch.object(AniWorldIndexer, 'fetch_with_retry')
    def test_aniworld_incremental_skips_existing(self, mock_fetch, mock_process):
        catalog_html = """
        <html>
            <body>
                <a href="/anime/stream/attack-on-titan">Attack on Titan</a>
                <a href="/anime/stream/demon-slayer">Demon Slayer</a>
            </body>
        </html>
        """
        mock_fetch.return_value.text = catalog_html
        mock_process.return_value = ("url", True, "ok")

        indexer = AniWorldIndexer(self.db, self.config_mock)

        s_url = "https://aniworld.to/anime/stream/attack-on-titan"
        indexer.save_series_to_db("Attack on Titan", "Desc", "cover.jpg", s_url, "Action", [(1, 1, "Ep 1", s_url + "/episode-1")])

        indexer.scrape_all_series(incremental=True)

        mock_process.assert_called_once_with("https://aniworld.to/anime/stream/demon-slayer")

    @patch.object(AniWorldIndexer, 'process_single_series')
    @patch.object(AniWorldIndexer, 'fetch_with_retry')
    def test_aniworld_full_reindexes_all(self, mock_fetch, mock_process):
        catalog_html = """
        <html>
            <body>
                <a href="/anime/stream/attack-on-titan">Attack on Titan</a>
                <a href="/anime/stream/demon-slayer">Demon Slayer</a>
            </body>
        </html>
        """
        mock_fetch.return_value.text = catalog_html
        mock_process.return_value = ("url", True, "ok")

        indexer = AniWorldIndexer(self.db, self.config_mock)

        s_url = "https://aniworld.to/anime/stream/attack-on-titan"
        indexer.save_series_to_db("Attack on Titan", "Desc", "cover.jpg", s_url, "Action", [(1, 1, "Ep 1", s_url + "/episode-1")])

        indexer.scrape_all_series(incremental=False)

        self.assertEqual(mock_process.call_count, 2)

    @patch.object(StoIndexer, 'process_single_series')
    @patch.object(StoIndexer, 'fetch_with_retry')
    def test_sto_incremental_skips_existing(self, mock_fetch, mock_process):
        catalog_html = """
        <html>
            <body>
                <a href="/serie/stream/breaking-bad">Breaking Bad</a>
                <a href="/serie/stream/better-call-saul">Better Call Saul</a>
            </body>
        </html>
        """
        mock_fetch.return_value.text = catalog_html
        mock_process.return_value = ("url", True, "ok")

        indexer = StoIndexer(self.db, self.config_mock)

        s_url = "http://186.2.175.5/serie/stream/breaking-bad"
        indexer.save_series_to_db("Breaking Bad", "Desc", "cover.jpg", s_url, "Drama", [(1, 1, "Ep 1", s_url + "/episode-1")])

        indexer.scrape_all_series(incremental=True)

        mock_process.assert_called_once_with("http://186.2.175.5/serie/stream/better-call-saul")

    @patch.object(FilmoIndexer, 'process_single_series')
    @patch.object(FilmoIndexer, 'fetch_with_retry')
    def test_filmo_incremental_skips_existing(self, mock_fetch, mock_process):
        catalog_html = """
        <html>
            <body>
                <a href="/movies/inception">Inception</a>
                <a href="/movies/interstellar">Interstellar</a>
            </body>
        </html>
        """
        mock_fetch.return_value.text = catalog_html
        mock_process.return_value = ("url", True, "ok")

        indexer = FilmoIndexer(self.db, self.config_mock)

        s_url = "https://filmo.to/movies/inception"
        indexer.save_series_to_db("Inception", "Desc", "cover.jpg", s_url, "Sci-Fi", [(1, 1, "Inception", s_url)])

        indexer.scrape_all_series(limit=10, incremental=True)

        mock_process.assert_called_once_with("https://filmo.to/movies/interstellar")

if __name__ == "__main__":
    unittest.main()
