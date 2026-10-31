package com.shadow.firetv.core.stream

import com.shadow.firetv.core.model.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HosterLink(
    val target: String,
    val languageKey: String,
    val hosterName: String
)

class HosterLinkResolver(
    private val client: OkHttpClient = defaultClient
) {

    suspend fun resolve(episodePageUrl: String, skip: Int = 0): Result<StreamResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val pageUrl = HttpUrl.parse(episodePageUrl) ?: throw IOException("Ungültige Episoden-URL")
            val html = fetch(pageUrl, referer = null).second
            val links = rankLinks(parseHosterLinks(html))
            val target = when {
                links.isNotEmpty() -> {
                    val link = links.getOrNull(skip) ?: throw IOException("Keine weiteren Hoster verfügbar")
                    pageUrl.resolve(link.target)
                }
                skip > 0 -> throw IOException("Keine weiteren Hoster verfügbar")
                else -> parseIframeSrc(html)?.let { pageUrl.resolve(it) }
            } ?: throw IOException("Kein Hoster auf der Episodenseite gefunden")

            val finalUrl = fetch(target, referer = pageUrl.toString()).first
            StreamResponse(
                stream_url = finalUrl,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to pageUrl.toString())
            )
        }
    }

    private fun fetch(url: HttpUrl, referer: String?): Pair<String, String> {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()} von ${url.host()}")
            return response.request().url().toString() to (response.body()?.string() ?: "")
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val LANGUAGE_PREFERENCE = listOf("1", "3", "2")
        private val HOSTER_PREFERENCE = listOf("voe", "vidmoly", "filemoon", "doodstream")

        private val LI_REGEX = Regex("""<li\b([^>]*\bdata-link-target\s*=[^>]*)>(.*?)</li>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val H4_REGEX = Regex("""<h4[^>]*>\s*([^<]+?)\s*</h4>""", RegexOption.IGNORE_CASE)
        private val IFRAME_REGEX = Regex("""<iframe\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(MemoryCookieJar())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        fun parseHosterLinks(html: String): List<HosterLink> {
            return LI_REGEX.findAll(html).mapNotNull { match ->
                val attributes = match.groupValues[1]
                val target = attribute(attributes, "data-link-target")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HosterLink(
                    target = target,
                    languageKey = attribute(attributes, "data-lang-key").orEmpty(),
                    hosterName = H4_REGEX.find(match.groupValues[2])?.groupValues?.get(1).orEmpty()
                )
            }.toList()
        }

        fun pickBest(links: List<HosterLink>): HosterLink? = rankLinks(links).firstOrNull()

        fun rankLinks(links: List<HosterLink>): List<HosterLink> {
            return links.sortedWith(
                compareBy<HosterLink> { rank(LANGUAGE_PREFERENCE, it.languageKey) }
                    .thenBy { rank(HOSTER_PREFERENCE, it.hosterName.lowercase()) }
            )
        }

        fun parseIframeSrc(html: String): String? {
            return IFRAME_REGEX.findAll(html)
                .map { it.groupValues[1] }
                .firstOrNull { it.isNotBlank() && it != "about:blank" }
        }

        private fun attribute(attributes: String, name: String): String? {
            val regex = Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            return regex.find(attributes)?.groupValues?.get(1)
        }

        private fun rank(preference: List<String>, value: String): Int {
            val index = preference.indexOf(value)
            return if (index == -1) preference.size else index
        }
    }
}

private class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host()
        store[host] = (store[host].orEmpty().filter { old -> cookies.none { it.name() == old.name() } }) + cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host()].orEmpty().filter { it.expiresAt() > now && it.matches(url) }
    }
}
