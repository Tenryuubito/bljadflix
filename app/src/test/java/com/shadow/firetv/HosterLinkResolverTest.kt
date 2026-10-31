package com.shadow.firetv

import com.shadow.firetv.core.stream.HosterLink
import com.shadow.firetv.core.stream.HosterLinkResolver
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HosterLinkResolverTest {

    private val episodeHtml = """
        <div class="hosterSiteVideo">
          <ul class="row">
            <li class="col-md-3 col-xs-12 col-sm-6 episodeLink3941737" data-lang-key="2" data-link-id="3941737" data-link-target="/redirect/3941737" data-external-embed="false">
              <div><a class="watchEpisode" itemprop="url" href="/redirect/3941737" target="_blank"><i class="icon VOE"></i><h4>VOE</h4></a></div>
            </li>
            <li class="col-md-3 col-xs-12 col-sm-6 episodeLink3941745" data-lang-key="3" data-link-id="3941745" data-link-target="/redirect/3941745" data-external-embed="false">
              <div><a class="watchEpisode" href="/redirect/3941745"><h4>Filemoon</h4></a></div>
            </li>
            <li class="col-md-3 col-xs-12 col-sm-6 episodeLink3941736" data-lang-key="3" data-link-id="3941736" data-link-target="/redirect/3941736" data-external-embed="false">
              <div><a class="watchEpisode" href="/redirect/3941736"><h4>VOE</h4></a></div>
            </li>
          </ul>
        </div>
    """.trimIndent()

    @Test
    fun `parses hoster links with language and name`() {
        val links = HosterLinkResolver.parseHosterLinks(episodeHtml)

        assertEquals(
            listOf(
                HosterLink("/redirect/3941737", "2", "VOE"),
                HosterLink("/redirect/3941745", "3", "Filemoon"),
                HosterLink("/redirect/3941736", "3", "VOE")
            ),
            links
        )
    }

    @Test
    fun `prefers german audio then german subtitles and voe`() {
        val links = HosterLinkResolver.parseHosterLinks(episodeHtml)
        assertEquals("/redirect/3941736", HosterLinkResolver.pickBest(links)?.target)

        val withGermanDub = links + HosterLink("/redirect/1", "1", "Doodstream")
        assertEquals("/redirect/1", HosterLinkResolver.pickBest(withGermanDub)?.target)

        assertNull(HosterLinkResolver.pickBest(emptyList()))
    }

    @Test
    fun `resolve follows best hoster link with episode page as referer`() = runTest {
        val requests = mutableListOf<Pair<String, String?>>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.url().toString() to request.header("Referer")
                val body = if (request.url().encodedPath().startsWith("/redirect/")) "" else episodeHtml
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(MediaType.parse("text/html"), body))
                    .build()
            }
            .build()

        val episodeUrl = "https://aniworld.to/anime/stream/test/staffel-1/episode-1"
        val result = HosterLinkResolver(client).resolve(episodeUrl)

        assertTrue(result.isSuccess)
        assertEquals("https://aniworld.to/redirect/3941736", result.getOrThrow().stream_url)
        assertEquals(episodeUrl, result.getOrThrow().headers["Referer"])
        assertEquals(listOf(episodeUrl to null, "https://aniworld.to/redirect/3941736" to episodeUrl), requests)
    }

    @Test
    fun `resolve fails when page has no hoster`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(MediaType.parse("text/html"), "<html></html>"))
                    .build()
            }
            .build()

        val result = HosterLinkResolver(client).resolve("https://aniworld.to/anime/stream/test/staffel-1/episode-1")

        assertTrue(result.isFailure)
    }
}
