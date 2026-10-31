import json
import re
import time
from typing import Optional
from urllib.parse import urlparse, urljoin, unquote
import requests
from bs4 import BeautifulSoup
from fastapi import HTTPException
from database import DatabaseManager
from config import Config

COMMON_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7"
}

class StreamService:
    def __init__(self, db: DatabaseManager, config: Config):
        self.db = db
        self.config = config

    def resolve_stream(self, episode_id: int, source: Optional[str] = "aniworld") -> dict:
        clean_source = self.db.sanitize_source(source)
        with self.db.get_content_connection(clean_source, row_factory=True) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT episode_url FROM episodes WHERE id = ?", (episode_id,))
            ep = cursor.fetchone()

            if not ep:
                raise HTTPException(status_code=404, detail="Episode not found")

            source_url = ep["episode_url"]

            cursor.execute("SELECT stream_url, expires_at FROM video_links WHERE episode_id = ?", (episode_id,))
            cached = cursor.fetchone()
            current_time = time.time()

            if cached and cached["expires_at"] > current_time and cached["stream_url"] != source_url:
                return {"stream_url": cached["stream_url"], "headers": COMMON_HEADERS}

        parsed_url = urlparse(source_url)
        origin = f"{parsed_url.scheme}://{parsed_url.netloc}"

        headers = dict(COMMON_HEADERS)
        headers.update({"Origin": origin, "Referer": source_url})

        session = requests.Session()
        try:
            response = session.get(source_url, headers=headers, timeout=10)
            response.raise_for_status()
        except requests.RequestException:
            raise HTTPException(status_code=502, detail="Failed to fetch episode page")

        soup = BeautifulSoup(response.text, 'lxml')
        stream_url = source_url

        chip = soup.find('div', attrs={'data-provider-chip': True}) or soup.find(attrs={'data-p': True})
        if chip and chip.get('data-p'):
            open_mint = f"{origin}/n"
            for sc in soup.find_all('script'):
                if sc.text and 'window.filmoLibrary' in sc.text:
                    try:
                        m_json = sc.text.split('window.filmoLibrary = ')[1].split(';')[0]
                        lib = json.loads(m_json)
                        open_mint = lib.get('urls', {}).get('openMint', open_mint)
                    except Exception:
                        pass

            data_p = chip.get('data-p')
            xsrf_cookie = session.cookies.get('XSRF-TOKEN')
            post_headers = dict(headers)
            post_headers.update({
                'Referer': source_url,
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/json'
            })
            if xsrf_cookie:
                post_headers['X-XSRF-TOKEN'] = unquote(xsrf_cookie)

            try:
                mint_res = session.post(open_mint, json={'p': data_p}, headers=post_headers, timeout=10)
                if mint_res.status_code == 200:
                    data = mint_res.json()
                    token_x = data.get('x')
                    if token_x:
                        iframe_src = open_mint.rstrip('/') + '/' + token_x
                        embed_res = session.get(iframe_src, headers={'Referer': source_url}, timeout=10, allow_redirects=True)
                        stream_url = embed_res.url
            except Exception:
                pass

        if stream_url == source_url:
            redirect_href = ""

            nav_el = (
                soup.find('ul', class_='hosterSiteDirectNav') or
                soup.find('div', class_='hosterSiteVideo') or
                soup
            )

            for tag in nav_el.find_all(['a', 'li', 'button']):
                rel_url = (
                    tag.get('data-link-target') or
                    tag.get('data-href') or
                    tag.get('href', '')
                )
                if rel_url and "/redirect/" in rel_url:
                    redirect_href = rel_url
                    break

            if redirect_href:
                redirect_full = urljoin(source_url, redirect_href)
                try:
                    redir_res = session.get(redirect_full, headers=headers, timeout=10, allow_redirects=True)
                    if redir_res.status_code == 200:
                        stream_url = redir_res.url
                except Exception:
                    pass

        if stream_url == source_url:
            iframe = soup.find('iframe')
            if iframe and iframe.get('src') and iframe.get('src') != 'about:blank':
                hoster_url = urljoin(source_url, iframe['src'])
                try:
                    hoster_res = session.get(hoster_url, headers=headers, timeout=10)
                    hoster_res.raise_for_status()

                    redirect_match = re.search(r"window\.location\.href\s*=\s*['\"](https?://[^'\"]+)['\"]", hoster_res.text)
                    if redirect_match:
                        final_hoster_url = redirect_match.group(1)
                        hoster_parsed = urlparse(final_hoster_url)
                        hoster_domain = f"{hoster_parsed.scheme}://{hoster_parsed.netloc}/"
                        headers.update({"Referer": hoster_domain, "Origin": hoster_domain[:-1]})

                        hoster_res = session.get(final_hoster_url, headers=headers, timeout=10)
                        hoster_res.raise_for_status()
                        stream_url = hoster_res.url
                    else:
                        stream_url = hoster_res.url
                except requests.RequestException:
                    stream_url = hoster_url
            else:
                video_tag = soup.find('video')
                if video_tag:
                    v_src = video_tag.get('src')
                    if not v_src:
                        source_tag = video_tag.find('source')
                        if source_tag:
                            v_src = source_tag.get('src')
                    if v_src:
                        stream_url = urljoin(source_url, v_src)

        if stream_url != source_url and not any(ext in stream_url.lower() for ext in ['.m3u8', '.mp4']):
            try:
                hoster_parsed = urlparse(stream_url)
                hoster_domain = f"{hoster_parsed.scheme}://{hoster_parsed.netloc}/"
                headers.update({"Referer": stream_url, "Origin": hoster_domain.rstrip('/')})

                h_res = session.get(stream_url, headers=headers, timeout=8)
                if h_res.status_code == 200:
                    media_match = (
                        re.search(r'file\s*:\s*["\'](https?://[^"\']+\.(?:m3u8|mp4)[^"\']*)["\']', h_res.text) or
                        re.search(r'src\s*:\s*["\'](https?://[^"\']+\.(?:m3u8|mp4)[^"\']*)["\']', h_res.text) or
                        re.search(r'<source\s+src=["\'](https?://[^"\']+)["\']', h_res.text)
                    )
                    if media_match:
                        stream_url = media_match.group(1)
            except Exception:
                pass

        with self.db.get_content_connection(clean_source) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM video_links WHERE episode_id = ?", (episode_id,))
            cursor.execute("INSERT INTO video_links (episode_id, stream_url, expires_at) VALUES (?, ?, ?)",
                           (episode_id, stream_url, current_time + 7200))
            conn.commit()

        return {"stream_url": stream_url, "headers": headers}

