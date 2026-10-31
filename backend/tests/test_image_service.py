import io
import os
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from config import Config
from database import DatabaseManager
from services.image_service import COVER_MAX_WIDTH, ImageService

try:
    from PIL import Image
except ImportError:
    Image = None


def fake_response(body: bytes, status: int = 200, content_type: str = "image/jpeg"):
    res = MagicMock()
    res.status_code = status
    res.headers = {"Content-Type": content_type}
    res.iter_content.return_value = [body]
    res.__enter__.return_value = res
    res.__exit__.return_value = False
    return res


class TestImageService(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.db = DatabaseManager(db_dir=self.temp_dir.name)
        self.db.init_db(sources=["aniworld", "filmo"])
        config = MagicMock(spec=Config)
        config.sources = {"aniworld": {}, "filmo": {}}
        self.service = ImageService(self.db, config)

        with self.db.get_content_connection("aniworld") as conn:
            conn.execute("INSERT INTO content_index (id, title, source_page_url, cover_url) VALUES (1, 'Show', 'https://aniworld.to/a', 'https://aniworld.to/cover.jpg')")
            conn.execute("INSERT INTO content_index (id, title, source_page_url, cover_url) VALUES (2, 'Ohne Bild', 'https://aniworld.to/b', '')")
            conn.commit()

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch("services.image_service.requests.get")
    def test_cover_is_fetched_once_and_cached(self, mock_get):
        mock_get.return_value = fake_response(b"bilddaten")

        first = self.service.get_cover_path("aniworld", 1)
        second = self.service.get_cover_path("aniworld", 1)

        self.assertIsNotNone(first)
        self.assertEqual(first, second)
        self.assertEqual(mock_get.call_count, 1)
        self.assertEqual(mock_get.call_args[0][0], "https://aniworld.to/cover.jpg")

    @patch("services.image_service.requests.get")
    def test_missing_cover_unknown_source_and_failed_fetch_return_none(self, mock_get):
        mock_get.return_value = fake_response(b"", status=404)

        self.assertIsNone(self.service.get_cover_path("aniworld", 2))
        self.assertIsNone(self.service.get_cover_path("aniworld", 999))
        self.assertIsNone(self.service.get_cover_path("unbekannt", 1))
        self.assertIsNone(self.service.get_cover_path("aniworld", 1))
        self.assertFalse(os.path.exists(os.path.join(self.temp_dir.name, "movies_unbekannt.db")))

    @patch("services.image_service.requests.get")
    def test_non_image_response_is_rejected(self, mock_get):
        mock_get.return_value = fake_response(b"<html>gesperrt</html>", content_type="text/html")
        self.assertIsNone(self.service.get_cover_path("aniworld", 1))

    @unittest.skipIf(Image is None, "Pillow nicht installiert")
    @patch("services.image_service.requests.get")
    def test_large_cover_is_scaled_down(self, mock_get):
        buffer = io.BytesIO()
        Image.new("RGB", (1080, 1620), "red").save(buffer, format="PNG")
        mock_get.return_value = fake_response(buffer.getvalue(), content_type="image/png")

        path = self.service.get_cover_path("aniworld", 1)

        with Image.open(path) as img:
            self.assertEqual(img.format, "JPEG")
            self.assertEqual(img.width, COVER_MAX_WIDTH)


if __name__ == "__main__":
    unittest.main()
