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
from services.indexers.filmo_indexer import FilmoIndexer

class TestFilmoIndexer(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db = DatabaseManager(db_dir=self.temp_dir.name)
        self.db.init_db(sources=["filmo"])
        
        self.config_mock = MagicMock(spec=Config)
        self.config_mock.sources = {
            "filmo": {"id": "filmo", "name": "Filmo", "url": "https://filmo.to"}
        }
        self.indexer = FilmoIndexer(self.db, self.config_mock)

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch.object(FilmoIndexer, 'fetch_with_retry')
    def test_process_single_movie(self, mock_fetch):
        movie_html = """
        <html>
            <head>
                <title>Inception - Filmo</title>
                <meta property="og:image" content="https://filmo.to/posters/inception.jpg" />
            </head>
            <body>
                <h1>Inception</h1>
                <p class="movie-detail-synopsis">A thief who steals corporate secrets through dream-sharing technology.</p>
                <div class="genres"><a href="/genres/sci-fi">Sci-Fi</a>, <a href="/genres/action">Action</a></div>
            </body>
        </html>
        """
        res = MagicMock()
        res.text = movie_html
        mock_fetch.return_value = res

        s_url = "https://filmo.to/movies/inception"
        url, success, msg = self.indexer.process_single_series(s_url)

        self.assertTrue(success)

        with self.db.get_content_connection("filmo", row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM content_index WHERE source_page_url = ?", (s_url,))
            content = cursor.fetchone()
            self.assertIsNotNone(content)
            self.assertEqual(content["title"], "Inception")
            self.assertEqual(content["cover_url"], "https://filmo.to/posters/inception.jpg")

            cursor.execute("SELECT * FROM episodes WHERE content_id = ?", (content["id"],))
            episodes = cursor.fetchall()
            self.assertEqual(len(episodes), 1)
            self.assertEqual(episodes[0]["season_number"], 1)
            self.assertEqual(episodes[0]["episode_number"], 1)

    @patch.object(FilmoIndexer, 'fetch_with_retry')
    def test_process_series_with_episodes(self, mock_fetch):
        series_html = """
        <html>
            <head><title>Stranger Things</title></head>
            <body>
                <h1>Stranger Things</h1>
                <div class="episodes-wrapper">
                    <a href="/series/stranger-things/season-1/episode-1">Staffel 1 Folge 1 - Chapter One</a>
                    <a href="/series/stranger-things/season-1/episode-2">Staffel 1 Folge 2 - Chapter Two</a>
                </div>
            </body>
        </html>
        """
        res = MagicMock()
        res.text = series_html
        mock_fetch.return_value = res

        s_url = "https://filmo.to/series/stranger-things"
        url, success, msg = self.indexer.process_single_series(s_url)

        self.assertTrue(success)

        with self.db.get_content_connection("filmo", row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM content_index WHERE source_page_url = ?", (s_url,))
            content = cursor.fetchone()

            cursor.execute("SELECT * FROM episodes WHERE content_id = ? ORDER BY season_number, episode_number", (content["id"],))
            episodes = [dict(r) for r in cursor.fetchall()]

            self.assertEqual(len(episodes), 2)
            self.assertEqual(episodes[0]["season_number"], 1)
            self.assertEqual(episodes[0]["episode_number"], 1)
            self.assertEqual(episodes[1]["episode_number"], 2)

if __name__ == "__main__":
    unittest.main()
