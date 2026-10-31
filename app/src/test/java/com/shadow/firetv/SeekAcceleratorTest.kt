package com.shadow.firetv

import com.shadow.firetv.feature.player.SeekAccelerator
import com.shadow.firetv.feature.player.SeekPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekAcceleratorTest {

    private val accelerator = SeekAccelerator()

    @Test
    fun `single presses accumulate from the start position`() {
        accelerator.step(1, 10, isRepeat = false, currentPosition = 100f, duration = 1000f, nowMs = 0)
        val preview = accelerator.step(1, 10, isRepeat = false, currentPosition = 105f, duration = 1000f, nowMs = 300)

        assertEquals(SeekPreview(20, 120f), preview)
        assertEquals(120f, accelerator.finish())
    }

    @Test
    fun `holding the key speeds up seeking`() {
        accelerator.step(1, 10, isRepeat = false, currentPosition = 0f, duration = 5000f, nowMs = 0)
        val slow = accelerator.step(1, 10, isRepeat = true, currentPosition = 0f, duration = 5000f, nowMs = 1000)
        val faster = accelerator.step(1, 10, isRepeat = true, currentPosition = 0f, duration = 5000f, nowMs = 2500)
        val fastest = accelerator.step(1, 10, isRepeat = true, currentPosition = 0f, duration = 5000f, nowMs = 6000)

        assertEquals(20, slow?.offsetSeconds)
        assertEquals(50, faster?.offsetSeconds)
        assertEquals(110, fastest?.offsetSeconds)
    }

    @Test
    fun `fast key repeats are throttled`() {
        accelerator.step(1, 10, isRepeat = false, currentPosition = 0f, duration = 1000f, nowMs = 0)

        assertNull(accelerator.step(1, 10, isRepeat = true, currentPosition = 0f, duration = 1000f, nowMs = 50))
    }

    @Test
    fun `target is clamped to the video bounds`() {
        val back = accelerator.step(-1, 30, isRepeat = false, currentPosition = 12f, duration = 1000f, nowMs = 0)
        assertEquals(SeekPreview(-12, 0f), back)
        accelerator.finish()

        val forward = accelerator.step(1, 30, isRepeat = false, currentPosition = 990f, duration = 1000f, nowMs = 1000)
        assertEquals(SeekPreview(9, 999f), forward)
    }

    @Test
    fun `finish without seeking returns null`() {
        assertNull(accelerator.finish())
    }
}
