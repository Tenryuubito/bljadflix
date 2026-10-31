import os
import sys
import time
import unittest

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

import tests
import app_backend


class TestContentListingSource(unittest.TestCase):

    USER = {"user_id": 4343}

    def setUp(self):
        self.title = f"Quellentest {time.time_ns()}"
        with app_backend.db.get_content_connection("sto") as conn:
            conn.execute(
                "INSERT INTO content_index (title, description, cover_url, source_page_url, genre) VALUES (?, ?, ?, ?, ?)",
                (self.title, "Beschreibung", "http://example.test/c.jpg", f"http://example.test/serie/{time.time_ns()}", "Krimi")
            )
            conn.commit()

    def _assert_sto(self, items):
        matching = [m for m in items if m.get("title") == self.title]
        self.assertTrue(matching)
        self.assertTrue(all(m["source"] == "sto" for m in matching))

    def test_search_results_carry_requested_source(self):
        self._assert_sto(app_backend.search_movies(query=self.title, genre="", page=1, limit=20, source="sto", user=self.USER))

    def test_genre_results_carry_requested_source(self):
        self._assert_sto(app_backend.get_movies_by_genre(genre_name="Krimi", page=1, limit=1000, source="sto", user=self.USER))

    def test_movie_list_carries_requested_source(self):
        self._assert_sto(app_backend.get_movies(page=1, limit=100000, source="sto", user=self.USER))


if __name__ == "__main__":
    unittest.main()
