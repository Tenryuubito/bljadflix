import os
import json
import threading
import uvicorn
from fastapi import APIRouter, FastAPI, HTTPException, BackgroundTasks, Header, Depends, Query, Request
from fastapi.responses import HTMLResponse, RedirectResponse, JSONResponse, FileResponse
from pydantic import BaseModel
from typing import Optional, List

from config import Config
from database import DatabaseManager
from models.auth import LoginRequest, LoginResponse, AuthStatusResponse, CreateUserRequest, UpdateUserRequest, UserDto
from models.content import MovieResponse, StreamResponse, ProgressRequest, WatchlistRequest, ContinueWatchingResponse
from services.auth_service import AuthService
from services.content_service import ContentService
from services.stream_service import StreamService
from services.image_service import ImageService
from services.indexer_service import IndexerService
from services.scheduler_service import SchedulerService
from services.admin_service import AdminService
from services.admin_auth_service import AdminAuthService, MAX_FAILED_ATTEMPTS
from services.indexer_logger import indexer_logger
from dashboard_view import get_dashboard_html

config = Config()
db = DatabaseManager()
db.init_db()

auth_service = AuthService(db, config)
content_service = ContentService(db)
stream_service = StreamService(db, config)
image_service = ImageService(db, config)
indexer_service = IndexerService(db, config)
scheduler_service = SchedulerService(indexer_service, config)
admin_service = AdminService(db)
admin_auth_service = AdminAuthService(config)

app = FastAPI(title="Fire TV Scraper Multi-Source Backend")


def get_client_ip(request: Request) -> str:
    client_host = request.client.host if request.client else "127.0.0.1"
    forwarded_for = request.headers.get("X-Forwarded-For")
    if forwarded_for and client_host in config.trusted_proxies:
        return forwarded_for.split(",")[-1].strip()
    return client_host


@app.middleware("http")
async def check_banned_devices_middleware(request: Request, call_next):
    client_ip = get_client_ip(request)
    device_id = request.headers.get("X-Device-ID")

    if auth_service.is_banned(client_ip, device_id):
        auth_service.record_banned_attempt(client_ip, device_id)
        return JSONResponse(
            status_code=403,
            content={"detail": "Zugriff verweigert: Dieses Gerät bzw. diese IP-Adresse ist gesperrt."}
        )

    return await call_next(request)


@app.on_event("startup")
def startup_check():
    scheduler_service.start()

    movies = content_service.get_movies(source=config.default_source, page=1, limit=1)
    if not movies:
        print("Standard-Datenbank ist leer! Starte Erst-Indexierung im Hintergrund...")
        threading.Thread(target=indexer_service.scrape_all_series, kwargs={"source": "all"}, daemon=True).start()


@app.on_event("shutdown")
def shutdown_event():
    scheduler_service.stop()


def get_current_user_optional(
    authorization: Optional[str] = Header(None, alias="Authorization"),
    x_device_id: Optional[str] = Header(None, alias="X-Device-ID")
) -> Optional[dict]:
    return auth_service.get_current_user_optional(authorization, x_device_id)


def get_current_user(
    authorization: Optional[str] = Header(None, alias="Authorization"),
    x_device_id: Optional[str] = Header(None, alias="X-Device-ID")
) -> dict:
    return auth_service.get_current_user(authorization, x_device_id)


def require_admin(x_admin_token: Optional[str] = Header(None, alias="X-Admin-Token")):
    if not admin_auth_service.is_configured():
        raise HTTPException(status_code=503, detail="Admin-Zugang deaktiviert: admin_password in config.json setzen.")
    if not admin_auth_service.verify_token(x_admin_token):
        raise HTTPException(status_code=401, detail="Admin-Anmeldung erforderlich.")


def require_admin_or_user(
    x_admin_token: Optional[str] = Header(None, alias="X-Admin-Token"),
    authorization: Optional[str] = Header(None, alias="Authorization"),
    x_device_id: Optional[str] = Header(None, alias="X-Device-ID")
):
    if admin_auth_service.verify_token(x_admin_token):
        return
    auth_service.get_current_user(authorization, x_device_id)


admin_router = APIRouter(dependencies=[Depends(require_admin)])


@app.post("/auth/login", response_model=LoginResponse)
def login(req: LoginRequest, request: Request):
    client_ip = get_client_ip(request)
    user_agent = request.headers.get("User-Agent", "")
    return auth_service.login(req, client_ip=client_ip, user_agent=user_agent)


@app.get("/auth/status", response_model=AuthStatusResponse)
def check_auth_status(
    authorization: Optional[str] = Header(None, alias="Authorization"),
    x_device_id: Optional[str] = Header(None, alias="X-Device-ID")
):
    try:
        user = auth_service.get_current_user_optional(authorization, x_device_id)
        if user:
            return AuthStatusResponse(is_valid=True, active_device_id=user["device_id"])
        return AuthStatusResponse(is_valid=False, message="Ungültige oder abgelaufene Sitzung")
    except HTTPException as e:
        return AuthStatusResponse(is_valid=False, message=e.detail)


@app.post("/auth/logout")
def logout(user: dict = Depends(get_current_user)):
    return auth_service.logout(user["user_id"])


@admin_router.post("/admin/users", response_model=UserDto)
def create_user(req: CreateUserRequest):
    return auth_service.create_user(req)


@admin_router.get("/admin/users", response_model=List[UserDto])
def list_users():
    return auth_service.list_users()


@admin_router.put("/admin/users/{user_id}")
def update_user(user_id: int, req: UpdateUserRequest):
    req.user_id = user_id
    return auth_service.update_user(req)


@admin_router.delete("/admin/users/{user_id}")
def delete_user(user_id: int):
    return auth_service.delete_user(user_id)


@app.get("/sources")
def get_sources(user: dict = Depends(get_current_user)):
    return config.sources


@app.get("/movies", response_model=List[MovieResponse])
def get_movies(
    page: int = 1,
    limit: int = 20,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    return content_service.get_movies(source=source, page=page, limit=limit)


@app.get("/genres", response_model=List[str])
def get_genres(
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    return content_service.get_genres(source=source)


@app.get("/movies/genre/{genre_name}", response_model=List[MovieResponse])
def get_movies_by_genre(
    genre_name: str,
    page: int = 1,
    limit: int = 20,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    return content_service.get_movies_by_genre(source=source, genre_name=genre_name, page=page, limit=limit)


@app.get("/search", response_model=List[MovieResponse])
def search_movies(
    query: str = "",
    genre: str = "",
    page: int = 1,
    limit: int = 20,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    results = content_service.search_movies(source=source, query=query, genre=genre, page=page, limit=limit)
    content_service.log_search(user_id=user_id, source=source, query=query, genre=genre, results_count=len(results))
    return results


@app.get("/movies/{content_id}/episodes")
def get_episodes(
    content_id: int,
    background_tasks: BackgroundTasks,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    source_url = content_service.get_source_page_url(source=source, content_id=content_id)
    if source_url:
        background_tasks.add_task(indexer_service.process_single_series_background, source_url, source)
    return content_service.get_episodes(source=source, content_id=content_id, user_id=user_id)


@app.post("/movies/{content_id}/reindex")
def reindex_single_series(
    content_id: int,
    background_tasks: BackgroundTasks,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    source_url = content_service.get_source_page_url(source=source, content_id=content_id)
    if not source_url:
        raise HTTPException(status_code=404, detail="Serie nicht gefunden oder keine Source-URL vorhanden.")
    background_tasks.add_task(indexer_service.process_single_series_background, source_url, source)
    return {"status": "success", "message": "Reindexierung im Hintergrund gestartet"}


@app.post("/progress")
def update_progress(
    req: ProgressRequest,
    source: Optional[str] = Query(None),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    req_source = source or req.source or "aniworld"
    return content_service.update_progress(user_id=user_id, source=req_source, req=req)


@app.get("/continue-watching", response_model=List[ContinueWatchingResponse])
def get_continue_watching(
    source: Optional[str] = Query(None),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    return content_service.get_continue_watching(user_id=user_id, source=source)


@app.delete("/continue-watching/{content_id}")
def hide_continue_watching(
    content_id: int,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    return content_service.hide_continue_watching(user_id=user_id, source=source, content_id=content_id)


@app.post("/watchlist")
def update_watchlist(
    req: WatchlistRequest,
    source: Optional[str] = Query(None),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    req_source = source or req.source or "aniworld"
    return content_service.update_watchlist(user_id=user_id, source=req_source, req=req)


@app.get("/watchlist/{content_id}")
def get_watchlist_status(
    content_id: int,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    return content_service.get_watchlist_status(user_id=user_id, source=source, content_id=content_id)


@app.get("/watchlist", response_model=List[MovieResponse])
def get_watchlist(
    page: int = 1,
    limit: int = 20,
    source: Optional[str] = Query(None),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    return content_service.get_watchlist(user_id=user_id, source=source, page=page, limit=limit)


@app.get("/cover/{source}/{content_id}")
def get_cover(source: str, content_id: int):
    path = image_service.get_cover_path(source, content_id)
    if not path:
        raise HTTPException(status_code=404, detail="Cover nicht verfügbar")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "public, max-age=604800"})


@app.get("/resolve-stream", response_model=StreamResponse)
def resolve_stream(
    episode_id: int,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    return stream_service.resolve_stream(episode_id=episode_id, source=source)


@app.get("/sync-status")
def get_sync_status(user: dict = Depends(get_current_user)):
    return indexer_service.get_sync_status()


@app.post("/sync-user-content")
def sync_user_content(
    background_tasks: BackgroundTasks,
    source: Optional[str] = Query("aniworld"),
    user: dict = Depends(get_current_user)
):
    user_id = user["user_id"]
    return indexer_service.sync_user_content(user_id=user_id, background_tasks=background_tasks, source=source)


@app.get("/", response_class=RedirectResponse)
def root_redirect():
    return RedirectResponse(url="/dashboard")


@app.get("/dashboard", response_class=HTMLResponse)
@app.get("/admin/dashboard", response_class=HTMLResponse)
def get_dashboard_page():
    return HTMLResponse(content=get_dashboard_html())


class AdminLoginRequest(BaseModel):
    password: str


class AdminUserActionRequest(BaseModel):
    user_id: int


class AdminSqlQueryRequest(BaseModel):
    db_name: str
    query: str


class UnbanDeviceRequest(BaseModel):
    id: int


class ManualBanRequest(BaseModel):
    ip_address: str
    device_id: Optional[str] = None
    device_name: Optional[str] = "Manuell gesperrt"
    reason: Optional[str] = "Manuelle Sperre durch Administrator"


@app.post("/admin/api/login")
def admin_login(req: AdminLoginRequest, request: Request):
    if not admin_auth_service.is_configured():
        raise HTTPException(status_code=503, detail="Admin-Zugang deaktiviert: admin_password in config.json setzen.")
    client_ip = get_client_ip(request)
    if admin_auth_service.is_locked_out(client_ip):
        raise HTTPException(status_code=429, detail="Zu viele Fehlversuche. Bitte in 15 Minuten erneut versuchen.")
    if not admin_auth_service.check_password(req.password):
        attempts = admin_auth_service.record_failure(client_ip)
        raise HTTPException(status_code=401, detail=f"Falsches Admin-Passwort. Versuche: {attempts}/{MAX_FAILED_ATTEMPTS}")
    admin_auth_service.clear_failures(client_ip)
    return {"token": admin_auth_service.create_token()}


@app.post("/admin/api/logout")
def admin_logout(x_admin_token: Optional[str] = Header(None, alias="X-Admin-Token")):
    admin_auth_service.revoke_token(x_admin_token)
    return {"status": "success"}


@admin_router.get("/admin/api/stats")
def get_admin_stats():
    return admin_service.get_dashboard_stats()


@admin_router.get("/admin/api/top-content")
def get_admin_top_content(limit: int = 10):
    return admin_service.get_top_watched_content(limit=limit)


@admin_router.get("/admin/api/charts-data")
def get_admin_charts_data():
    return admin_service.get_activity_charts_data()


@admin_router.get("/admin/api/watch-logs")
def get_admin_watch_logs(
    user_id: Optional[int] = Query(None),
    source: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    page: int = 1,
    limit: int = 30
):
    return admin_service.get_watch_logs(
        user_id=user_id,
        source=source,
        search=search,
        page=page,
        limit=limit
    )


@admin_router.get("/admin/api/users")
def get_admin_users():
    return admin_service.get_users_list()


@admin_router.get("/admin/api/users/{user_id}/details")
def get_admin_user_details(user_id: int):
    return admin_service.get_user_details(user_id)


@admin_router.get("/admin/api/users/{user_id}/watch-logs")
def get_admin_user_watch_logs(
    user_id: int,
    source: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    page: int = 1,
    limit: int = 30
):
    return admin_service.get_watch_logs(
        user_id=user_id,
        source=source,
        search=search,
        page=page,
        limit=limit
    )


@admin_router.get("/admin/api/users/{user_id}/watchlist")
def get_admin_user_watchlist(user_id: int):
    return admin_service.get_user_watchlist_details(user_id)


@admin_router.get("/admin/api/users/{user_id}/searches")
def get_admin_user_searches(
    user_id: int,
    page: int = 1,
    limit: int = 30
):
    return admin_service.get_user_search_history(user_id=user_id, page=page, limit=limit)


@admin_router.post("/admin/api/users/toggle-status")
def toggle_user_status(req: AdminUserActionRequest):
    return admin_service.toggle_user_status(req.user_id)


@admin_router.post("/admin/api/users/reset-session")
def reset_user_session(req: AdminUserActionRequest):
    return admin_service.reset_user_session(req.user_id)


@admin_router.get("/admin/api/banned-devices")
def get_banned_devices():
    return auth_service.get_banned_devices()


@admin_router.post("/admin/api/banned-devices/unban")
def unban_device(req: UnbanDeviceRequest):
    return auth_service.unban_device(req.id)


@admin_router.post("/admin/api/banned-devices/ban")
def manual_ban_device(req: ManualBanRequest):
    return auth_service.ban_device_manually(
        ip_address=req.ip_address,
        device_id=req.device_id,
        device_name=req.device_name,
        reason=req.reason
    )


@admin_router.get("/admin/api/indexer/logs")
def get_indexer_logs(since_id: int = Query(0)):
    return {
        "is_indexing": indexer_service.is_indexing,
        "is_syncing": indexer_service.is_syncing,
        "logs": indexer_logger.get_logs(since_id=since_id)
    }


@admin_router.post("/admin/api/indexer/clear-logs")
def clear_indexer_logs():
    indexer_logger.clear()
    return {"status": "success"}


@admin_router.get("/admin/api/db/tables")
def get_admin_db_tables(db_name: str = Query("users")):
    return admin_service.get_db_tables(db_name=db_name)


@admin_router.get("/admin/api/db/data")
def get_admin_db_data(
    db_name: str = Query("users"),
    table_name: str = Query("users"),
    page: int = 1,
    limit: int = 30,
    search: str = Query("")
):
    return admin_service.get_table_data(
        db_name=db_name,
        table_name=table_name,
        page=page,
        limit=limit,
        search=search
    )


@admin_router.post("/admin/api/db/query")
def execute_admin_sql_query(req: AdminSqlQueryRequest):
    return admin_service.execute_sql_query(db_name=req.db_name, query=req.query)


@admin_router.get("/admin/scheduler/status")
def get_scheduler_status():
    return scheduler_service.get_status()


@admin_router.post("/admin/scheduler/trigger")
def trigger_scheduler_now():
    return scheduler_service.trigger_now()


@app.post("/admin/trigger-index")
def trigger_index(
    background_tasks: BackgroundTasks,
    source: Optional[str] = Query("all"),
    incremental: bool = Query(True),
    _: None = Depends(require_admin_or_user)
):
    def scrape_series_task(src: str, inc: bool):
        try:
            indexer_service.scrape_all_series(source=src, incremental=inc)
        except Exception as e:
            print(f"Fehler im Indexer-Task ({src}): {e}")

    background_tasks.add_task(scrape_series_task, source or "all", incremental)
    return {"status": "success", "source": source or "all", "incremental": incremental}


@app.get("/app/version")
def get_app_version():
    updates_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "updates")
    os.makedirs(updates_dir, exist_ok=True)
    
    version_file = os.path.join(updates_dir, "version.json")
    if os.path.exists(version_file):
        try:
            with open(version_file, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"Error reading version.json: {e}")

    return {
        "launcher": {
            "versionCode": 1,
            "versionName": "1.0.0",
            "packageName": "com.shadow.firetv.installer",
            "downloadUrl": "/app/download/launcher"
        },
        "mainApp": {
            "packageName": "com.shadow.firetv",
            "versionCode": 1,
            "versionName": "1.0.0",
            "downloadUrl": "/app/download/main",
            "minLauncherVersionCode": 1,
            "changelog": "Standard Release - Fire TV Bljadflix Client"
        }
    }


@app.get("/app/download/main")
def download_main_app():
    updates_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "updates")
    apk_path = os.path.join(updates_dir, "bljadflix-main.apk")
    
    if not os.path.exists(apk_path):
        project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        fallback_apk = os.path.join(project_root, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
        if os.path.exists(fallback_apk):
            apk_path = fallback_apk

    if not os.path.exists(apk_path):
        raise HTTPException(status_code=404, detail="Haupt-App APK file nicht auf dem Server gefunden.")
        
    return FileResponse(
        path=apk_path,
        media_type="application/vnd.android.package-archive",
        filename="bljadflix-main.apk"
    )


@app.get("/install", response_class=RedirectResponse)
def install_redirect():
    return RedirectResponse(url="/app/download/launcher")


@app.get("/app/download/launcher")
def download_launcher_app():
    updates_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "updates")
    apk_path = os.path.join(updates_dir, "bljadflix-launcher.apk")
    
    if not os.path.exists(apk_path):
        project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        fallback_apk = os.path.join(project_root, "installer", "build", "outputs", "apk", "debug", "installer-debug.apk")
        if os.path.exists(fallback_apk):
            apk_path = fallback_apk

    if not os.path.exists(apk_path):
        raise HTTPException(status_code=404, detail="Launcher APK file nicht auf dem Server gefunden.")
        
    return FileResponse(
        path=apk_path,
        media_type="application/vnd.android.package-archive",
        filename="bljadflix-launcher.apk"
    )


app.include_router(admin_router)


if __name__ == "__main__":
    uvicorn.run("app_backend:app", host="0.0.0.0", port=config.backend_port, reload=True)

