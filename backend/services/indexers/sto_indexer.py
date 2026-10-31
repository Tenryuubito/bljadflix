import concurrent.futures
import re
from typing import Tuple, Optional
from urllib.parse import urljoin
from bs4 import BeautifulSoup

from .base_indexer import BaseIndexer
from database import DatabaseManager
from config import Config

class StoIndexer(BaseIndexer):
    def __init__(self, db: DatabaseManager, config: Config):
        source_url = config.sources.get("sto", {}).get("url", "http://186.2.175.5")
        super().__init__("sto", source_url, db, config)

    def process_single_series(self, s_url: str) -> Tuple[str, bool, str]:
        try:
            detail_res = self.fetch_with_retry(s_url)
            detail_soup = BeautifulSoup(detail_res.text, 'lxml')

            title_tag = detail_soup.find('h1') or detail_soup.find('title')
            title = title_tag.text.strip() if title_tag else "Unbekannte Serie"
            if " - S.to" in title or " | S.to" in title:
                title = title.replace(" - S.to", "").replace(" | S.to", "").strip()

            desc_tag = (
                detail_soup.find('p', class_='seri_des') or
                detail_soup.find('span', class_='description-text') or
                detail_soup.find('p', class_='serien-beschreibung') or
                detail_soup.find('div', class_='description') or
                detail_soup.find('p', class_='description')
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
            cover_container = detail_soup.find('div', class_='col-3 col-md-3 col-lg-2 d-none d-md-block') or detail_soup.find('div', class_='seriesCoverBox')
            if cover_container:
                img_tag = cover_container.find('img')
                if not img_tag and cover_container.find('picture'):
                    img_tag = cover_container.find('picture').find('img')
                if img_tag:
                    cover_url = img_tag.get('data-src') or img_tag.get('data-original') or img_tag.get('src', '')
                    if cover_url.startswith('data:image'): cover_url = ""
                    if cover_url:
                        cover_url = urljoin(s_url, cover_url)

            genres_list = [a.text.strip() for a in detail_soup.find_all('a', href=True) if "/genre/" in a['href'].lower() and a.text.strip()]
            genre_str = ", ".join(sorted(list(set(genres_list)))) if genres_list else "Allgemein"

            season_links = set([s_url])
            season_nav = (
                detail_soup.find(id='season-nav') or
                detail_soup.find('ul', class_='seasonNav') or
                detail_soup.find('div', class_='seasons')
            )
            nav_scope = season_nav or detail_soup
            for a in nav_scope.find_all('a', href=True):
                href = a['href']
                if "staffel-" in href or "filme" in href or "season-" in href:
                    full_s_url = urljoin(s_url, href)
                    season_links.add(full_s_url)

            episodes_data = []
            for season_url in season_links:
                try:
                    s_res = self.fetch_with_retry(season_url) if season_url != s_url else detail_res
                    s_soup = BeautifulSoup(s_res.text, 'lxml')

                    is_movie_page = "filme" in season_url.lower() or "film-" in season_url.lower()

                    rows = s_soup.find_all('tr', class_='episode-row')
                    if not rows:
                        table = s_soup.find('table', class_=re.compile(r'episode-table')) or s_soup.find('table')
                        if table:
                            rows = table.find_all('tr')

                    for row_idx, row in enumerate(rows, start=1):
                        ep_href = ""
                        onclick = row.get('onclick', '')
                        if onclick and "window.location=" in onclick:
                            ep_href = onclick.split("'")[1] if "'" in onclick else onclick.split('"')[1]
                        else:
                            a_tag = row.find('a', href=True)
                            if a_tag:
                                ep_href = a_tag['href']

                        if not ep_href:
                            continue

                        full_ep_url = urljoin(season_url, ep_href)

                        if is_movie_page or "/filme" in ep_href.lower() or "film-" in ep_href.lower():
                            s_num = 0
                        else:
                            s_match = re.search(r'(?:staffel|season)-(\d+)', ep_href) or re.search(r'(?:staffel|season)-(\d+)', season_url)
                            s_num = int(s_match.group(1)) if s_match else 1

                        e_match = re.search(r'(?:episode|film|folge)-(\d+)', ep_href) or re.search(r'/(\d+)$', ep_href)
                        e_num = int(e_match.group(1)) if e_match else row_idx

                        title_ger = row.find('strong', class_='episode-title-ger') or row.find('span', class_='episode-title-ger')
                        title_eng = row.find('span', class_='episode-title-eng') or row.find('strong', class_='episode-title-eng')
                        ger_text = title_ger.text.strip() if title_ger else ""
                        eng_text = title_eng.text.strip() if title_eng else ""

                        fallback_title = f"Film {e_num}" if s_num == 0 else f"Folge {e_num}"
                        parsed_text = ger_text or eng_text or fallback_title

                        if not any(e[3] == full_ep_url for e in episodes_data):
                            episodes_data.append((s_num, e_num, parsed_text, full_ep_url))
                except Exception:
                    continue

            self.save_series_to_db(title, description, cover_url, s_url, genre_str, episodes_data)
            return s_url, True, f"{len(episodes_data)} Episoden aus {len(season_links)} Staffeln"

        except Exception as e:
            return s_url, False, str(e)

    def scrape_all_series(self, limit: Optional[int] = None, incremental: bool = True):
        base_catalog = f"{self.base_url}/serien"
        mode_str = "Inkrementelle " if incremental else "Vollständige "
        self.log(f"Starte {mode_str}Indexierung für S.to ({self.base_url})...", "INFO")
        try:
            response = self.fetch_with_retry(base_catalog)
            soup = BeautifulSoup(response.text, 'lxml')

            series_links = list(set(
                urljoin(self.base_url, a['href'])
                for a in soup.find_all('a', href=True) if "/serie/" in a['href']
            ))

            all_links = sorted(series_links)
            total_found = len(all_links)

            if incremental:
                existing_urls = self.get_indexed_urls()
                links_list = [u for u in all_links if u.rstrip('/') not in existing_urls]
                skipped_count = total_found - len(links_list)
                if skipped_count > 0:
                    self.log(f"{skipped_count} von {total_found} Serien-Links bereits in DB (übersprungen).", "INFO")
            else:
                links_list = all_links

            if limit and limit > 0:
                links_list = links_list[:limit]

            total_series = len(links_list)
            if total_series == 0:
                self.log("Keine neuen Serien zum Indexieren vorhanden.", "SUCCESS")
                return

            self.log(f"{total_series} Serien-Links werden indexiert...", "INFO")

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
                        log_text = f"[{completed}/{total_series}] {status}: {slug} -> {msg}"
                        self.log(log_text, "SUCCESS" if success else "ERROR")
                    except Exception as exc:
                        self.log(f"[{completed}/{total_series}] Kritischer Fehler bei {url}: {exc}", "ERROR")

            self.log(f"Indexierung für S.to erfolgreich abgeschlossen! ({completed}/{total_series})", "SUCCESS")
        except Exception as e:
            self.log(f"Kritischer Fehler bei S.to Hauptindexierung: {e}", "ERROR")
