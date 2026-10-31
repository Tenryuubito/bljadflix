package com.shadow.firetv

import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SourceManagerTest {

    private lateinit var sourceManager: SourceManager

    @Before
    fun setup() {
        sourceManager = SourceManager.createForTesting()
    }

    @Test
    fun `default selected source is ANIWORLD`() {
        val source = sourceManager.getSelectedSource()
        assertEquals(StreamingSource.ANIWORLD, source)
        assertEquals("https://aniworld.to/", source.url)
        assertEquals("1", source.key)
    }

    @Test
    fun `setSelectedSource persists and updates active source`() {
        sourceManager.setSelectedSource(StreamingSource.STO)
        assertEquals(StreamingSource.STO, sourceManager.getSelectedSource())
        assertEquals("http://186.2.175.5/", sourceManager.getSelectedSource().url)
        assertEquals("2", sourceManager.getSelectedSource().key)

        sourceManager.setSelectedSource(StreamingSource.FILMO)
        assertEquals(StreamingSource.FILMO, sourceManager.getSelectedSource())
        assertEquals("https://filmo.to/", sourceManager.getSelectedSource().url)
        assertEquals("3", sourceManager.getSelectedSource().key)
    }

    @Test
    fun `StreamingSource fromId maps ids keys and urls correctly`() {
        assertEquals(StreamingSource.ANIWORLD, StreamingSource.fromId("aniworld"))
        assertEquals(StreamingSource.ANIWORLD, StreamingSource.fromId("1"))
        assertEquals(StreamingSource.ANIWORLD, StreamingSource.fromId("https://aniworld.to/"))

        assertEquals(StreamingSource.STO, StreamingSource.fromId("sto"))
        assertEquals(StreamingSource.STO, StreamingSource.fromId("2"))
        assertEquals(StreamingSource.STO, StreamingSource.fromId("http://186.2.175.5/"))

        assertEquals(StreamingSource.FILMO, StreamingSource.fromId("filmo"))
        assertEquals(StreamingSource.FILMO, StreamingSource.fromId("3"))
        assertEquals(StreamingSource.FILMO, StreamingSource.fromId("https://filmo.to/"))

        assertEquals(StreamingSource.ANIWORLD, StreamingSource.fromId("unknown_id"))
    }
}
