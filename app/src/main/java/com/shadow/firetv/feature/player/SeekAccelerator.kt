package com.shadow.firetv.feature.player

import kotlin.math.roundToInt

data class SeekPreview(val offsetSeconds: Int, val targetSeconds: Float)

class SeekAccelerator {
    private var active = false
    private var startPosition = 0f
    private var target = 0f
    private var holdStartedAt = -1L
    private var lastStepAt = -1L

    fun step(
        direction: Int,
        baseSeconds: Int,
        isRepeat: Boolean,
        currentPosition: Float,
        duration: Float,
        nowMs: Long
    ): SeekPreview? {
        if (!active) {
            active = true
            startPosition = currentPosition
            target = currentPosition
        }
        if (!isRepeat || holdStartedAt < 0) {
            holdStartedAt = nowMs
        } else if (lastStepAt >= 0 && nowMs - lastStepAt < REPEAT_INTERVAL_MS) {
            return null
        }

        val heldMs = if (isRepeat) nowMs - holdStartedAt else 0L
        val multiplier = when {
            heldMs >= FASTEST_AFTER_MS -> 6
            heldMs >= FASTER_AFTER_MS -> 3
            else -> 1
        }
        val maxTarget = if (duration > 0f) (duration - 1f).coerceAtLeast(0f) else Float.MAX_VALUE
        target = (target + direction * baseSeconds * multiplier).coerceIn(0f, maxTarget)
        lastStepAt = nowMs
        return SeekPreview((target - startPosition).roundToInt(), target)
    }

    fun finish(): Float? {
        if (!active) return null
        active = false
        holdStartedAt = -1L
        lastStepAt = -1L
        return target
    }

    companion object {
        const val REPEAT_INTERVAL_MS = 150L
        const val FASTER_AFTER_MS = 2000L
        const val FASTEST_AFTER_MS = 5000L
    }
}
