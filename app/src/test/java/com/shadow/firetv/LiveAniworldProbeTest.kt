package com.shadow.firetv

import com.shadow.firetv.core.stream.HosterLinkResolver
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LiveAniworldProbeTest {
    @Test
    fun probe() = runBlocking {
        for (url in listOf(
            "https://aniworld.to/anime/stream/one-piece/staffel-1/episode-1",
            "https://aniworld.to/anime/stream/100-metres/staffel-1/episode-1"
        )) {
            val r = HosterLinkResolver().resolve(url)
            println("LIVEPROBE $url -> ${r.getOrNull()?.stream_url ?: r.exceptionOrNull()}")
        }
    }
}
