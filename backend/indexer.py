import argparse
import os
from config import Config
from database import DatabaseManager
from services.indexer_service import IndexerService

_config = Config()
_db = DatabaseManager()
_indexer_service = IndexerService(_db, _config)

def process_single_series(s_url: str, source: str = "aniworld"):
    return _indexer_service.process_single_series(s_url, source=source)

def scrape_all_series(source: str = "all", limit: int = None, incremental: bool = True):
    return _indexer_service.scrape_all_series(source=source, limit=limit, incremental=incremental)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Multi-Source Fire TV Backend Indexer.")
    parser.add_argument("--source", type=str, default="all", help="Quelle, die indexiert werden soll (aniworld, sto, filmo, oder all).")
    parser.add_argument("--limit", type=int, default=None, help="Anzahl der zu indexierenden Serien je Quelle.")
    parser.add_argument("--full", action="store_true", help="Vollständige Re-Indexierung durchführen (nicht inkrementell).")
    args = parser.parse_args()
    
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    scrape_all_series(source=args.source, limit=args.limit, incremental=not args.full)
