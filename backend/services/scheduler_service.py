import threading
import time
from typing import Optional, Dict
from config import Config
from .indexer_service import IndexerService

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    APSCHEDULER_AVAILABLE = True
except ImportError:
    APSCHEDULER_AVAILABLE = False


class SchedulerService:
    def __init__(self, indexer_service: IndexerService, config: Config):
        self.indexer_service = indexer_service
        self.config = config
        self.lock = threading.Lock()
        self.last_run: Optional[float] = None
        self.next_run: Optional[float] = None
        self.last_status: str = "Inaktiv"
        self.is_running: bool = False
        
        self.aps_scheduler = None
        self.thread_worker = None
        self._stop_event = threading.Event()

    def _run_job(self):
        with self.lock:
            self.last_run = time.time()
            self.last_status = "Aktiv - Indexierung läuft..."

        try:
            print("[Scheduler] Starte geplante automatische Katalog-Indexierung...")
            self.indexer_service.scrape_all_series(source="all")
            with self.lock:
                self.last_status = "Erfolgreich abgeschlossen"
                interval_sec = self.config.indexer_interval_hours * 3600
                self.next_run = time.time() + interval_sec
            print(f"[Scheduler] Geplante Indexierung abgeschlossen. Nächster Lauf in {self.config.indexer_interval_hours} Stunden.")
        except Exception as e:
            with self.lock:
                self.last_status = f"Fehler: {str(e)}"
            print(f"[Scheduler] Fehler bei automatischer Indexierung: {e}")

    def _thread_loop(self):
        interval_sec = self.config.indexer_interval_hours * 3600
        while not self._stop_event.is_set():
            with self.lock:
                self.next_run = time.time() + interval_sec
            
            if self._stop_event.wait(timeout=interval_sec):
                break
                
            if not self._stop_event.is_set():
                self._run_job()

    def start(self):
        with self.lock:
            if self.is_running:
                return
            self.is_running = True
            self._stop_event.clear()

        interval_hours = self.config.indexer_interval_hours
        print(f"[Scheduler] Starte Scheduler (Intervall: {interval_hours} Std.)...")

        if APSCHEDULER_AVAILABLE:
            self.aps_scheduler = BackgroundScheduler(daemon=True)
            self.aps_scheduler.add_job(
                func=self._run_job,
                trigger="interval",
                hours=interval_hours,
                id="content_indexer_job",
                replace_existing=True
            )
            self.aps_scheduler.start()
            with self.lock:
                self.next_run = time.time() + (interval_hours * 3600)
                self.last_status = "Scheduler aktiv (APScheduler)"
        else:
            self.thread_worker = threading.Thread(target=self._thread_loop, daemon=True)
            self.thread_worker.start()
            with self.lock:
                self.last_status = "Scheduler aktiv (Fallback Thread)"

    def stop(self):
        with self.lock:
            if not self.is_running:
                return
            self.is_running = False

        self._stop_event.set()
        if self.aps_scheduler and self.aps_scheduler.running:
            self.aps_scheduler.shutdown(wait=False)
            self.aps_scheduler = None

        with self.lock:
            self.last_status = "Angehalten"
        print("[Scheduler] Scheduler wurde beendet.")

    def trigger_now(self):
        print("[Scheduler] Manuelle Auslösung angefordert...")
        t = threading.Thread(target=self._run_job, daemon=True)
        t.start()
        return {"status": "started", "message": "Indexierung im Hintergrund gestartet"}

    def get_status(self) -> Dict:
        with self.lock:
            return {
                "is_running": self.is_running,
                "interval_hours": self.config.indexer_interval_hours,
                "last_run": self.last_run,
                "next_run": self.next_run,
                "status": self.last_status,
                "engine": "APScheduler" if APSCHEDULER_AVAILABLE else "ThreadLoop"
            }
