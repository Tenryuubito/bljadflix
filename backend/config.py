import os
import json
import sys

DEFAULT_SOURCES = {
    "aniworld": {
        "id": "aniworld",
        "name": "AniWorld",
        "url": "https://aniworld.to"
    },
    "sto": {
        "id": "sto",
        "name": "S.to",
        "url": "http://186.2.175.5"
    },
    "filmo": {
        "id": "filmo",
        "name": "Filmo",
        "url": "https://filmo.to"
    }
}

class Config:
    def __init__(self, config_path: str = None):
        if config_path is None:
            config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config.json")
        self.config_path = config_path
        self.data = self._load_config()

    def _load_config(self) -> dict:
        if not os.path.exists(self.config_path):
            print("FEHLER: config.json nicht gefunden! Bitte führe zuerst 'python setup.py' im Hauptverzeichnis aus.")
            sys.exit(1)
        with open(self.config_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if "streaming_source_ip" not in data or "backend_port" not in data:
            print("FEHLER: config.json ist unvollständig. Bitte 'python setup.py' erneut ausführen.")
            sys.exit(1)
        return data

    @property
    def streaming_source_ip(self) -> str:
        return self.data["streaming_source_ip"]

    @property
    def backend_port(self) -> int:
        return int(self.data["backend_port"])

    @property
    def backend_ip(self) -> str:
        return self.data.get("backend_ip", "0.0.0.0")

    @property
    def sources(self) -> dict:
        loaded = self.data.get("sources", {})
        merged = dict(DEFAULT_SOURCES)
        if isinstance(loaded, dict):
            for k, v in loaded.items():
                if isinstance(v, dict):
                    merged[k] = {**merged.get(k, {}), **v}
        return merged

    @property
    def default_source(self) -> str:
        return self.data.get("default_source", "aniworld")

    @property
    def indexer_interval_hours(self) -> int:
        return int(self.data.get("indexer_interval_hours", 12))
