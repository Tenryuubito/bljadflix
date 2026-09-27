import time
import threading
from typing import List, Dict, Any, Optional

class IndexerLogger:
    _instance = None
    _lock = threading.Lock()

    def __init__(self, max_entries: int = 1000):
        self.max_entries = max_entries
        self.logs: List[Dict[str, Any]] = []
        self.counter = 0
        self.lock = threading.Lock()

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = IndexerLogger()
        return cls._instance

    def log(self, message: str, level: str = "INFO", source: str = "SYSTEM"):
        with self.lock:
            self.counter += 1
            entry = {
                "id": self.counter,
                "timestamp": time.strftime("%H:%M:%S"),
                "level": level.upper(),
                "source": source.upper(),
                "message": message
            }
            self.logs.append(entry)
            if len(self.logs) > self.max_entries:
                self.logs.pop(0)

            # Print to standard console
            try:
                print(f"[{entry['timestamp']}] [{entry['source']}] [{entry['level']}] {message}")
            except Exception:
                pass

    def get_logs(self, since_id: int = 0) -> List[Dict[str, Any]]:
        with self.lock:
            if since_id == 0:
                # Limit initial dump to last 300 entries for performance
                return list(self.logs[-300:])
            return [l for l in self.logs if l["id"] > since_id]

    def clear(self):
        with self.lock:
            self.logs.clear()
            self.counter = 0

indexer_logger = IndexerLogger.get_instance()
