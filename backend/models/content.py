from pydantic import BaseModel
from typing import Optional

class MovieResponse(BaseModel):
    id: int
    title: str
    description: str
    cover_url: str
    source_page_url: str
    genre: str = ""
    source: Optional[str] = None

class StreamResponse(BaseModel):
    stream_url: str
    headers: dict

class ProgressRequest(BaseModel):
    episode_id: int
    content_id: int
    progress_seconds: float
    duration_seconds: float
    is_completed: bool
    source: Optional[str] = "aniworld"

class WatchlistRequest(BaseModel):
    content_id: int
    is_watchlisted: bool
    source: Optional[str] = "aniworld"

class ContinueWatchingResponse(BaseModel):
    id: int
    title: str
    description: Optional[str] = None
    cover_url: Optional[str] = None
    genre: Optional[str] = None
    source: Optional[str] = "aniworld"
    episode_id: int
    season_number: int
    episode_number: int
    episode_title: Optional[str] = None
    progress_seconds: float
    duration_seconds: float
