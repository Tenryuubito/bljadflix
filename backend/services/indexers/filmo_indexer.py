import concurrent.futures
import json
import re
import sys
from typing import Tuple, Optional
from urllib.parse import urljoin
from bs4 import BeautifulSoup

from .base_indexer import BaseIndexer
from database import DatabaseManager
from config import Config

class FilmoIndexer(BaseIndexer):
    def __init__(self, db: DatabaseManager, config: Config):
        source_url = config.sources.get("filmo", {}).get("url", "https://filmo.to")
        super().__init__("filmo", source_url, db, config)

    def process_single_series(self, s_url: str) -> Tuple[str, bool, str]:
        try:
            detail_res = self.fetch_with_retry(s_url)
            detail_soup = BeautifulSoup(detail_res.text, 'lxml')

            title_tag = detail_soup.find('h1') or detail_soup.find('title')
            title = title_tag.text.strip() if title_tag else "Unbekannter Filmo Inhalt"
            if " - Filmo" in title:
                title = title.replace(" - Filmo", "").strip()

            desc_tag = (
                detail_soup.find('p', class_='seri_des') or
                detail_soup.find('div', class_='movie-detail-synopsis-block') or
                detail_soup.find('p', class_='movie-detail-synopsis') or
                detail_soup.find('div', class_='description') or
                detail_soup.find('p', class_='overview') or
                detail_soup.find('span', class_='description-text')
            )
            description = ""
            if desc_tag:
                description = desc_tag.get('data-full-description') or desc_tag.text.strip()
                if description.endswith("mehr anzeigen"):
                    description = description[:-13].strip()
                elif "…mehr anzeigen" in description:
                    description = description.replace("…mehr anzeigen", "").strip()
                elif "...mehr anzeigen" in description:
                    description = description.replace("...mehr anzeigen", "").strip()
            if not description:
                meta_desc = detail_soup.find('meta', attrs={'name': 'description'}) or detail_soup.find('meta', attrs={'property': 'og:description'})
                if meta_desc and meta_desc.get('content'):
                    description = meta_desc['content'].strip()
            if not description:
                description = "Keine Beschreibung verfügbar."

            cover_url = ""
            og_img = detail_soup.find('meta', attrs={'property': 'og:image'})
            if og_img and og_img.get('content'):
                cover_url = og_img['content'].strip()

            if not cover_url:
                cover_img = detail_soup.find('img', class_='img-fluid') or detail_soup.find('img', class_='poster') or detail_soup.find('div', class_='poster')
                if cover_img:
                    if cover_img.name == 'img':
                        cover_url = cover_img.get('data-src') or cover_img.get('src', '')
                    else:
                        img = cover_img.find('img')
                        if img:
                            cover_url = img.get('data-src') or img.get('src', '')

            if cover_url:
                cover_url = urljoin(s_url, cover_url)

            genres = []
            genres_block = detail_soup.find(lambda tag: tag.name in ['p', 'div'] and 'genres' in tag.text.lower() and len(tag.text) < 200)
            if genres_block:
                genres = [a.text.strip() for a in genres_block.find_all('a', href=True) if a.text.strip()]
            if not genres:
                genres = [
                    a.text.strip()
                    for a in detail_soup.find_all('a', href=True)
                    if ("/genres/" in a['href'].lower()) and a.text.strip() and len(a.text.strip()) < 30
                ]
            genre_str = ", ".join(sorted(list(set(genres)))) if genres else "Filme"

            episodes_data = []

            # Check if this page contains season/episode structure for series
            ep_elements = (
                detail_soup.find_all('a', href=re.compile(r'/(?:season|episode|staffel|folge|series|tv)/', re.I)) or
                detail_soup.find_all('div', class_=re.compile(r'(?:episode|season|item)', re.I)) or
                detail_soup.find_all('li', class_=re.compile(r'(?:episode|season|item)', re.I))
            )

            for idx, el in enumerate(ep_elements, start=1):
                ep_href = ""
                if el.name == 'a' and el.has_attr('href'):
                    ep_href = el['href']
                else:
                    a_tag = el.find('a', href=True)
                    if a_tag:
                        ep_href = a_tag['href']

                if not ep_href or "/genres/" in ep_href or "/collections/" in ep_href:
                    continue

                full_ep_url = urljoin(s_url, ep_href)

                s_match = re.search(r'(?:season|staffel|s)-?(\d+)', ep_href, re.I)
                s_num = int(s_match.group(1)) if s_match else 1

                e_match = re.search(r'(?:episode|folge|e)-?(\d+)', ep_href, re.I)
                e_num = int(e_match.group(1)) if e_match else idx

                ep_title = el.text.strip() if el.text else f"Folge {e_num}"
                if len(ep_title) > 80:
                    ep_title = f"Folge {e_num}"

                if not any(e[3] == full_ep_url for e in episodes_data):
                    episodes_data.append((s_num, e_num, ep_title, full_ep_url))

            if not episodes_data:
                episodes_data = [(1, 1, title, s_url)]

            self.save_series_to_db(title, description, cover_url, s_url, genre_str, episodes_data)
            return s_url, True, f"{len(episodes_data)} Episoden ({genre_str})"

        except Exception as e:
            return s_url, False, str(e)

    def scrape_all_series(self, limit: Optional[int] = None, incremental: bool = True):
        mode_str = "Inkrementelle " if incremental else "Vollständige "
        self.log(f"Starte {mode_str}Indexierung für Filmo ({self.base_url})...", "INFO")
        content_links = set()

        max_catalog_pages = 10 if not limit else min(15, max(2, limit // 20))
        catalog_bases = [
            f"{self.base_url}/movies",
            f"{self.base_url}/series",
            f"{self.base_url}/tv-shows",
            f"{self.base_url}/popular",
            f"{self.base_url}/letters/all"
        ]

        for base in catalog_bases:
            for page in range(1, max_catalog_pages + 1):
                cat_url = f"{base}?page={page}"
                try:
                    res = self.fetch_with_retry(cat_url)
                    soup = BeautifulSoup(res.text, 'lxml')
                    found_on_page = 0
                    for a in soup.find_all('a', href=True):
                        href = a['href']
                        if any(k in href.lower() for k in ["/movies/", "/series/", "/tv/", "/show/", "/tv-shows/"]):
                            if not any(href.lower().endswith(k) for k in ["/movies", "/series", "/tv-shows", "/shows"]) and "page=" not in href and "/genres/" not in href and "/collections/" not in href:
                                full = urljoin(self.base_url, href)
                                if full not in content_links:
                                    content_links.add(full)
                                    found_on_page += 1
                    if found_on_page == 0 and page > 1:
                        break
                except Exception as e:
                    self.log(f"Hinweis beim Laden der Filmo Katalog-Seite {cat_url}: {e}", "WARN")
                    break

        all_links = sorted(list(content_links))
        total_found = len(all_links)

        if incremental:
            existing_urls = self.get_indexed_urls()
            links_list = [u for u in all_links if u.rstrip('/') not in existing_urls]
            skipped_count = total_found - len(links_list)
            if skipped_count > 0:
                self.log(f"{skipped_count} von {total_found} Filmo-Links bereits in DB (übersprungen).", "INFO")
        else:
            links_list = all_links

        if limit and limit > 0:
            links_list = links_list[:limit]

        total_items = len(links_list)
        if total_items == 0:
            self.log("Keine neuen Filmo-Inhalte zum Indexieren vorhanden.", "SUCCESS")
            return

        self.log(f"{total_items} Filmo-Links werden indexiert...", "INFO")

        completed = 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            future_to_url = {executor.submit(self.process_single_series, url): url for url in links_list}
            for future in concurrent.futures.as_completed(future_to_url):
                completed += 1
                url = future_to_url[future]
                try:
                    _, success, msg = future.result()
                    slug = url.rstrip('/').split('/')[-1]
                    status = "Erfolg" if success else "Fehler"
                    log_text = f"[{completed}/{total_items}] {status}: {slug} -> {msg}"
                    self.log(log_text, "SUCCESS" if success else "ERROR")
                except Exception as exc:
                    self.log(f"[{completed}/{total_items}] Kritischer Fehler bei {url}: {exc}", "ERROR")

        self.log(f"Indexierung für Filmo erfolgreich abgeschlossen! ({completed}/{total_items})", "SUCCESS")

